package com.gurbakir.storefront

import java.io.IOException
import java.net.Proxy
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val MAX_MEDIA_REDIRECT_HOPS = 5
private const val HTTP_REDIRECT_MINIMUM = 300
private const val HTTP_REDIRECT_MAXIMUM = 399
private const val HTTP_MOVED_PERMANENTLY = 301
private const val HTTP_FOUND = 302
private const val HTTP_SEE_OTHER = 303
private const val HTTP_TEMPORARY_REDIRECT = 307
private const val HTTP_PERMANENT_REDIRECT = 308
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
    fun create(policy: StorefrontMediaPolicy): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(StorefrontMediaPolicyInterceptor(policy))
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .proxy(Proxy.NO_PROXY)
        .build()
}

internal class StorefrontMediaPolicyInterceptor(private val policy: StorefrontMediaPolicy) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request().withoutSensitiveMediaHeaders()
        if (request.method != "GET") rejectMediaRequest()

        val visited = linkedSetOf<String>()
        var redirectHops = 0
        while (true) {
            val rawUrl = request.url.toString()
            if (!policy.accepts(rawUrl) || !visited.add(rawUrl)) rejectMediaRequest()

            val response = chain.proceed(request)
            if (response.code !in HTTP_REDIRECT_MINIMUM..HTTP_REDIRECT_MAXIMUM) return response

            if (response.code !in PERMITTED_MEDIA_REDIRECT_CODES || redirectHops >= MAX_MEDIA_REDIRECT_HOPS) {
                response.close()
                rejectMediaRequest()
            }
            val target = response.header("Location")?.let(request.url::resolve)
            if (target == null || !policy.accepts(target.toString())) {
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
}

private fun Request.withoutSensitiveMediaHeaders(): Request = newBuilder().removeSensitiveMediaHeaders().build()

private fun Request.Builder.removeSensitiveMediaHeaders(): Request.Builder = apply {
    SENSITIVE_MEDIA_HEADERS.forEach(::removeHeader)
}

private fun rejectMediaRequest(): Nothing = throw IOException("Media request was rejected.")
