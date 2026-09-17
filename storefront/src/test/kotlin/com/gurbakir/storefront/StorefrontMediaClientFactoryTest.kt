package com.gurbakir.storefront

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StorefrontMediaClientFactoryTest {
    private val policy = StorefrontMediaPolicy("gurbakir.com")

    @Test
    fun `production client is credentialless fail closed and policy first`() {
        val client = StorefrontMediaClientFactory.create(policy)

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertSame(Authenticator.NONE, client.authenticator)
        assertSame(Authenticator.NONE, client.proxyAuthenticator)
        assertSame(Proxy.NO_PROXY, client.proxy)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
        assertInstanceOf(StorefrontMediaPolicyInterceptor::class.java, client.interceptors.first())
    }

    @Test
    fun `initial rejection reaches neither a later interceptor nor DNS`() {
        val downstreamCalls = AtomicInteger()
        val dnsCalls = AtomicInteger()
        val client =
            StorefrontMediaClientFactory.create(policy)
                .newBuilder()
                .addInterceptor { chain ->
                    downstreamCalls.incrementAndGet()
                    chain.proceed(chain.request())
                }
                .dns(
                    Dns {
                        dnsCalls.incrementAndGet()
                        listOf(InetAddress.getLoopbackAddress())
                    }
                )
                .build()

        assertThrows<IOException> {
            client.newCall(Request.Builder().url("https://rejected.invalid/file.jpg").build()).execute()
        }
        assertEquals(0, downstreamCalls.get())
        assertEquals(0, dnsCalls.get())
    }

    @Test
    fun `only GET is permitted before downstream execution`() {
        val responder = RecordingResponder { request, _ -> response(request, 200) }
        val client = clientWith(responder)

        assertThrows<IOException> {
            client.newCall(
                Request.Builder()
                    .url("https://gurbakir.com/cdn/shop/files/image.jpg")
                    .post(ByteArray(0).toRequestBody())
                    .build()
            ).execute()
        }
        assertTrue(responder.requests.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [301, 302, 303, 307, 308])
    fun `permitted redirects are manually followed after each target is validated`(code: Int) {
        val responder =
            RecordingResponder { request, call ->
                if (call == 1) {
                    response(request, code, location = "/cdn/shop/files/final.jpg?width=640")
                } else {
                    response(request, 200)
                }
            }

        clientWith(responder)
            .newCall(Request.Builder().url("https://gurbakir.com/cdn/shop/files/start.jpg?source=1").build())
            .execute()
            .use { final -> assertEquals(200, final.code) }

        assertEquals(2, responder.requests.size)
        assertEquals(
            "https://gurbakir.com/cdn/shop/files/final.jpg?width=640",
            responder.requests.last().url.toString()
        )
    }

    @Test
    fun `rejected redirect closes the intermediate body and never proceeds twice`() {
        val body = TrackingResponseBody()
        val responder =
            RecordingResponder { request, _ ->
                response(request, 302, location = "https://evil.invalid/image.jpg", body = body)
            }

        assertThrows<IOException> {
            clientWith(responder)
                .newCall(Request.Builder().url("https://gurbakir.com/cdn/shop/files/start.jpg").build())
                .execute()
        }

        assertEquals(1, responder.requests.size)
        assertTrue(body.closed)
    }

    @Test
    fun `cross host redirect strips ambient credential headers`() {
        val responder =
            RecordingResponder { request, call ->
                if (call == 1) {
                    response(request, 302, location = "https://cdn.shopify.com/s/files/1/final.jpg")
                } else {
                    response(request, 200)
                }
            }
        val request =
            Request.Builder()
                .url("https://gurbakir.com/cdn/shop/files/start.jpg")
                .header("Authorization", "Bearer secret")
                .header("Proxy-Authorization", "Basic secret")
                .header("Cookie", "session=secret")
                .header("X-Shopify-Storefront-Access-Token", "secret")
                .build()

        clientWith(responder).newCall(request).execute().close()

        val initial = responder.requests.first()
        val redirected = responder.requests.last()
        assertEquals("cdn.shopify.com", redirected.url.host)
        listOf(
            "Authorization",
            "Proxy-Authorization",
            "Cookie",
            "X-Shopify-Storefront-Access-Token"
        ).forEach { header ->
            assertEquals(null, initial.header(header))
            assertEquals(null, redirected.header(header))
        }
    }

    @Test
    fun `loops malformed locations and unsupported redirects fail closed`() {
        listOf<RecordingResponder>(
            RecordingResponder { request, _ -> response(request, 302, location = request.url.toString()) },
            RecordingResponder { request, _ -> response(request, 302) },
            RecordingResponder { request, _ -> response(request, 302, location = "http://[broken") },
            RecordingResponder { request, _ -> response(request, 304) }
        ).forEach { responder ->
            assertThrows<IOException> {
                clientWith(responder)
                    .newCall(Request.Builder().url("https://gurbakir.com/cdn/shop/files/hop-1.jpg").build())
                    .execute()
            }
        }
    }

    @Test
    fun `at most five redirect hops are followed`() {
        val responder =
            RecordingResponder { request, call ->
                response(request, 302, location = "/cdn/shop/files/hop-${call + 1}.jpg")
            }

        assertThrows<IOException> {
            clientWith(responder)
                .newCall(Request.Builder().url("https://gurbakir.com/cdn/shop/files/hop-1.jpg").build())
                .execute()
        }

        assertEquals(6, responder.requests.size)
        assertEquals("/cdn/shop/files/hop-6.jpg", responder.requests.last().url.encodedPath)
    }

    @Test
    fun `failures use generic messages without URL or credential data`() {
        val failure =
            assertThrows<IOException> {
                clientWith(
                    RecordingResponder { request, _ ->
                        response(request, 302, location = "https://evil.invalid/secret")
                    }
                )
                    .newCall(
                        Request.Builder()
                            .url("https://gurbakir.com/cdn/shop/files/start.jpg")
                            .header("Authorization", "Bearer secret-value")
                            .build()
                    ).execute()
            }

        assertFalse(failure.message.orEmpty().contains("gurbakir", ignoreCase = true))
        assertFalse(failure.message.orEmpty().contains("evil", ignoreCase = true))
        assertFalse(failure.message.orEmpty().contains("secret", ignoreCase = true))
    }

    @Test
    fun `playback guard sees each response byte including a full body returned to range`() {
        val guard = RecordingMediaRequestGuard()
        val body = "0123456789abcdef".toByteArray()
        val responder = RecordingResponder { request, _ -> response(request, 200, body = body.toResponseBody()) }
        val client = clientWith(responder, guard)

        client.newCall(
            Request.Builder()
                .url("https://gurbakir.com/cdn/shop/files/video.mp4")
                .header("Range", "bytes=8-")
                .build()
        ).execute().use { response -> response.body.bytes() }

        assertEquals(1, guard.startedUrls.size)
        assertEquals(body.size.toLong(), guard.responseBytes)
    }

    @Test
    fun `playback guard can reject a redirect before its target is requested`() {
        val guard = RecordingMediaRequestGuard(allowRedirects = false)
        val responder =
            RecordingResponder { request, _ -> response(request, 302, location = "/cdn/shop/files/final.mp4") }

        assertThrows<StorefrontMediaRejectedException> {
            clientWith(responder, guard)
                .newCall(Request.Builder().url("https://gurbakir.com/cdn/shop/files/start.mp4").build())
                .execute()
        }

        assertEquals(1, responder.requests.size)
        assertEquals(1, guard.redirectedUrls.size)
    }

    @Test
    fun `request guard factory creates an independent budget for every HTTP call`() {
        val createdGuards = AtomicInteger()
        val responder =
            RecordingResponder { request, _ -> response(request, 200, body = byteArrayOf(1).toResponseBody()) }
        val client =
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .addInterceptor(
                    StorefrontPerRequestMediaPolicyInterceptor(policy) {
                        createdGuards.incrementAndGet()
                        RecordingMediaRequestGuard()
                    }
                )
                .addInterceptor(responder)
                .build()

        repeat(2) {
            client.newCall(
                Request.Builder().url("https://gurbakir.com/cdn/shop/files/image-$it.jpg").build()
            ).execute().use { response -> response.body.bytes() }
        }

        assertEquals(2, createdGuards.get())
        assertEquals(2, responder.requests.size)
    }

    private fun clientWith(responder: Interceptor, guard: StorefrontMediaRequestGuard? = null): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .addInterceptor(StorefrontMediaPolicyInterceptor(policy, guard))
            .addInterceptor(responder)
            .build()

    private class RecordingResponder(private val block: (request: Request, call: Int) -> Response) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            requests += chain.request()
            return block(chain.request(), requests.size)
        }
    }

    private class TrackingResponseBody : ResponseBody() {
        private val trackingSource =
            object : ForwardingSource(Buffer().writeUtf8("redirect")) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }.buffer()

        var closed: Boolean = false
            private set

        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = 8

        override fun source(): BufferedSource = trackingSource
    }

    private class RecordingMediaRequestGuard(private val allowRedirects: Boolean = true) : StorefrontMediaRequestGuard {
        val startedUrls = mutableListOf<String>()
        val redirectedUrls = mutableListOf<String>()
        var responseBytes: Long = 0
            private set

        override fun onRequestStarted(url: String): Boolean {
            startedUrls += url
            return true
        }

        override fun onRedirect(targetUrl: String): Boolean {
            redirectedUrls += targetUrl
            return allowRedirects
        }

        override fun onResponseBytes(byteCount: Long): Boolean {
            responseBytes += byteCount
            return true
        }
    }
}

private fun response(
    request: Request,
    code: Int,
    location: String? = null,
    body: ResponseBody = TrackingEmptyResponseBody
): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_1_1)
    .code(code)
    .message("test")
    .body(body)
    .apply { location?.let { header("Location", it) } }
    .build()

private object TrackingEmptyResponseBody : ResponseBody() {
    private val buffer = Buffer()

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = 0

    override fun source(): BufferedSource = buffer
}

private fun ByteArray.toResponseBody(): ResponseBody = object : ResponseBody() {
    private val buffer = Buffer().write(this@toResponseBody)

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = this@toResponseBody.size.toLong()

    override fun source(): BufferedSource = buffer
}
