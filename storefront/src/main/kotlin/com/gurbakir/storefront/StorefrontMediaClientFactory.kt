package com.gurbakir.storefront

import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

private const val MAX_MEDIA_REDIRECT_HOPS = 5
private const val HTTP_REDIRECT_MINIMUM = 300
private const val HTTP_REDIRECT_MAXIMUM = 399
private const val HTTP_MOVED_PERMANENTLY = 301
private const val HTTP_FOUND = 302
private const val HTTP_SEE_OTHER = 303
private const val HTTP_TEMPORARY_REDIRECT = 307
private const val HTTP_PERMANENT_REDIRECT = 308
private const val MEDIA_CONNECT_TIMEOUT_SECONDS = 10L
private const val MEDIA_READ_TIMEOUT_SECONDS = 20L
private val PERMITTED_MEDIA_REDIRECT_CODES =
    setOf(
        HTTP_MOVED_PERMANENTLY,
        HTTP_FOUND,
        HTTP_SEE_OTHER,
        HTTP_TEMPORARY_REDIRECT,
        HTTP_PERMANENT_REDIRECT
    )
private val SENSITIVE_MEDIA_HEADERS =
    listOf(
        "Authorization",
        "Proxy-Authorization",
        "Cookie",
        "X-Shopify-Storefront-Access-Token"
    )

object StorefrontMediaClientFactory {
    fun create(policy: StorefrontMediaPolicy, requestGuard: StorefrontMediaRequestGuard? = null): OkHttpClient =
        create(StorefrontMediaPolicyInterceptor(policy, requestGuard))

    fun createWithRequestGuardFactory(
        policy: StorefrontMediaPolicy,
        requestGuardFactory: () -> StorefrontMediaRequestGuard
    ): OkHttpClient = create(StorefrontPerRequestMediaPolicyInterceptor(policy, requestGuardFactory))

    private fun create(policyInterceptor: Interceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(policyInterceptor)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(MEDIA_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(MEDIA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

internal class StorefrontPerRequestMediaPolicyInterceptor(
    private val policy: StorefrontMediaPolicy,
    private val requestGuardFactory: () -> StorefrontMediaRequestGuard
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        StorefrontMediaPolicyInterceptor(policy, requestGuardFactory()).intercept(chain)
}

interface StorefrontMediaRequestGuard {
    fun onRequestStarted(url: String): Boolean

    fun onRedirect(targetUrl: String): Boolean

    fun maximumResponseBytesForRead(requestedByteCount: Long): Long = requestedByteCount

    fun onResponseBytes(byteCount: Long): Boolean
}

class StorefrontMediaRejectedException : IOException("Media request was rejected.")

class StorefrontMediaLimitExceededException : IOException("Media response limit was exceeded.")

internal class StorefrontMediaPolicyInterceptor(
    private val policy: StorefrontMediaPolicy,
    private val requestGuard: StorefrontMediaRequestGuard? = null
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request().withoutSensitiveMediaHeaders()
        if (request.method != "GET") rejectMediaRequest()

        val visited = linkedSetOf<String>()
        var redirectHops = 0
        while (true) {
            validateOutgoingRequest(request, visited, redirectHops)

            val response = chain.proceed(request)
            if (response.code !in HTTP_REDIRECT_MINIMUM..HTTP_REDIRECT_MAXIMUM) {
                return response.withGuardedBody(requestGuard)
            }

            if (response.code !in PERMITTED_MEDIA_REDIRECT_CODES || redirectHops >= MAX_MEDIA_REDIRECT_HOPS) {
                response.close()
                rejectMediaRequest()
            }
            val target = response.header("Location")?.let(request.url::resolve)
            if (
                target == null ||
                !policy.accepts(target.toString()) ||
                requestGuard?.onRedirect(target.toString()) == false
            ) {
                response.close()
                rejectMediaRequest()
            }

            val crossHost = request.url.host != target.host
            response.close()
            request =
                request.newBuilder()
                    .url(target)
                    .apply { if (crossHost) removeSensitiveMediaHeaders() }
                    .build()
            redirectHops += 1
        }
    }

    private fun validateOutgoingRequest(request: Request, visited: MutableSet<String>, redirectHops: Int) {
        val rawUrl = request.url.toString()
        if (!policy.accepts(rawUrl)) rejectMediaRequest()
        if (!visited.add(rawUrl)) rejectMediaRequest()
        if (redirectHops == 0 && requestGuard?.onRequestStarted(rawUrl) == false) rejectMediaRequest()
    }
}

private fun Response.withGuardedBody(requestGuard: StorefrontMediaRequestGuard?): Response {
    val originalBody = body
    if (requestGuard == null) return this
    return newBuilder().body(GuardedResponseBody(originalBody, requestGuard)).build()
}

private class GuardedResponseBody(
    private val delegate: ResponseBody,
    private val requestGuard: StorefrontMediaRequestGuard
) : ResponseBody() {
    private val guardedSource: BufferedSource by lazy {
        object : ForwardingSource(delegate.source()) {
            override fun read(sink: okio.Buffer, byteCount: Long): Long {
                val permittedByteCount = requestGuard.maximumResponseBytesForRead(byteCount)
                if (permittedByteCount <= 0) throw StorefrontMediaLimitExceededException()
                val read = super.read(sink, minOf(byteCount, permittedByteCount))
                if (read > 0 && !requestGuard.onResponseBytes(read)) throw StorefrontMediaLimitExceededException()
                return read
            }
        }.buffer()
    }

    override fun contentType() = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource = guardedSource
}

private fun Request.withoutSensitiveMediaHeaders(): Request = newBuilder().removeSensitiveMediaHeaders().build()

private fun Request.Builder.removeSensitiveMediaHeaders(): Request.Builder = apply {
    SENSITIVE_MEDIA_HEADERS.forEach(::removeHeader)
}

private fun rejectMediaRequest(): Nothing = throw StorefrontMediaRejectedException()
