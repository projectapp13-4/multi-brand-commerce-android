package com.gurbakir.mobile.home

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.storefront.StorefrontMediaRequestGuard
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePlaybackDataSourceTest {
    private val rendition =
        HomeVideoRendition(
            url = "https://cdn.shopify.com/videos/home.mp4",
            mimeType = "video/mp4",
            format = "mp4",
            width = 1280,
            height = 720
        )

    @Test
    fun realMedia3DelegateWrapsPolicyAndCancellationWithoutAutomaticRetry() {
        listOf(false, true).forEach { canceled ->
            val attempt = attempt()
            val opens = AtomicInteger()
            val factory = HomePlaybackDataSourceFactory(attempt, rendition) { guard ->
                val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor {
                    opens.incrementAndGet()
                    check(guard.onRequestStarted(rendition.url))
                    if (canceled) throw IOException("Canceled")
                    check(!guard.onRedirect(rendition.url))
                    throw com.gurbakir.storefront.StorefrontMediaRejectedException()
                }.build()
                OkHttpDataSource.Factory(client).createDataSource()
            }
            val source = factory.createDataSource()
            try {
                val failure = runCatching { source.open(DataSpec(Uri.parse(rendition.url))) }.exceptionOrNull()
                assertTrue(failure is androidx.media3.datasource.HttpDataSource.HttpDataSourceException)
                assertEquals(1, opens.get())
                if (canceled) {
                    assertFalse(rendition in attempt.rejectedRenditions)
                } else {
                    assertTrue(rendition in attempt.rejectedRenditions)
                }
            } finally {
                source.close()
            }
        }
    }

    @Test
    fun realMedia3DelegateAllowsOneSameUrlTransportRetryWithinTheSameBudget() {
        val attempt = attempt(maxBytes = 5)
        val opens = AtomicInteger()
        val factory = HomePlaybackDataSourceFactory(attempt, rendition) { guard ->
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                check(guard.onRequestStarted(rendition.url))
                if (opens.incrementAndGet() == 1) throw java.net.SocketTimeoutException("transport")
                check(guard.onResponseBytes(4))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(byteArrayOf(1, 2, 3, 4).toResponseBody()).build()
            }.build()
            OkHttpDataSource.Factory(client).createDataSource()
        }
        read(factory.createDataSource(), position = 0, requested = 4)
        assertEquals(2, opens.get())
        assertEquals(4, attempt.responseBytesRead)
        assertEquals(1, attempt.remainingResponseBytes())
        assertFalse(rendition in attempt.rejectedRenditions)
    }

    @Test
    fun interruptingPendingRealMedia3OpenCancelsWithoutRetryOrLosingTheAttempt() {
        val attempt = attempt()
        assertTrue(attempt.recordResponseBytes(7))
        val entered = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        val opens = AtomicInteger()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor {
            opens.incrementAndGet()
            entered.countDown()
            finish.await(5, java.util.concurrent.TimeUnit.SECONDS)
            throw IOException("Canceled")
        }.build()
        val factory = HomePlaybackDataSourceFactory(attempt, rendition) { _ ->
            OkHttpDataSource.Factory(client).createDataSource()
        }
        val source = factory.createDataSource()
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val worker = Thread {
            runCatching { source.open(DataSpec(Uri.parse(rendition.url))) }.exceptionOrNull()?.let(failure::set)
        }
        try {
            worker.start()
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            worker.interrupt()
            worker.join(3_000)
            assertFalse(worker.isAlive)
            assertTrue(failure.get() is androidx.media3.datasource.HttpDataSource.HttpDataSourceException)
            assertEquals(1, opens.get())
            assertFalse(rendition in attempt.rejectedRenditions)
            assertEquals(HomePlaybackAttemptState.ACTIVE, attempt.state)
            assertEquals(7, attempt.responseBytesRead)
        } finally {
            finish.countDown()
            worker.interrupt()
            source.close()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun rangeAndSeekReuseTheSameAttemptAndCumulativeByteBudget() {
        val attempt = attempt(maxBytes = 10)
        val openedPositions = mutableListOf<Long>()
        val factory =
            HomePlaybackDataSourceFactory(attempt, rendition) { guard ->
                GuardedScriptedDataSource(guard, byteArrayOf(1, 2, 3, 4, 5), openedPositions)
            }

        read(factory.createDataSource(), position = 0, requested = 4)
        read(factory.createDataSource(), position = 4, requested = 3)
        read(factory.createDataSource(), position = 7, requested = 3)

        assertEquals(listOf(0L, 4L, 7L), openedPositions)
        assertEquals(10, attempt.responseBytesRead)
        assertEquals(HomePlaybackAttemptState.ACTIVE, attempt.state)
    }

    @Test
    fun oneReadTransportFailureReopensAtDeliveredOffsetOnlyOnce() {
        val attempt = attempt()
        val created = AtomicInteger()
        val openedPositions = mutableListOf<Long>()
        val factory =
            HomePlaybackDataSourceFactory(attempt, rendition) { guard ->
                val index = created.getAndIncrement()
                GuardedScriptedDataSource(
                    guard = guard,
                    bytes = byteArrayOf(7, 8, 9),
                    openedPositions = openedPositions,
                    failFirstRead = index == 0
                )
            }
        val source = factory.createDataSource()
        source.open(DataSpec(Uri.parse(rendition.url), 5, 3))

        val buffer = ByteArray(3)
        assertEquals(3, source.read(buffer, 0, buffer.size))
        source.close()

        assertEquals(2, created.get())
        assertEquals(listOf(5L, 5L), openedPositions)
        assertEquals(3, attempt.responseBytesRead)
    }

    @Test
    fun secondTransportFailureIsNotRetried() {
        val attempt = attempt()
        val created = AtomicInteger()
        val factory =
            HomePlaybackDataSourceFactory(attempt, rendition) { guard ->
                created.incrementAndGet()
                GuardedScriptedDataSource(
                    guard = guard,
                    bytes = byteArrayOf(1),
                    openedPositions = mutableListOf(),
                    failFirstRead = true
                )
            }
        val source = factory.createDataSource()
        source.open(DataSpec(Uri.parse(rendition.url)))

        val failure = runCatching { source.read(ByteArray(1), 0, 1) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(2, created.get())
        assertTrue(rendition in attempt.rejectedRenditions)
    }

    private fun read(source: DataSource, position: Long, requested: Int) {
        source.open(DataSpec(Uri.parse(rendition.url), position, requested.toLong()))
        assertEquals(requested, source.read(ByteArray(requested), 0, requested))
        source.close()
    }

    private fun attempt(maxBytes: Long = 32L * 1024L * 1024L): HomePlaybackAttempt = HomePlaybackAttempt(
        id = 17,
        clock = HomePlaybackClock { 0 },
        limits = HomePlaybackLimits(maxResponseBytes = maxBytes)
    ).also(HomePlaybackAttempt::start)
}

private class GuardedScriptedDataSource(
    private val guard: StorefrontMediaRequestGuard,
    private val bytes: ByteArray,
    private val openedPositions: MutableList<Long>,
    private val failFirstRead: Boolean = false
) : DataSource {
    private var failed = false
    private var offset = 0

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        check(guard.onRequestStarted(dataSpec.uri.toString()))
        openedPositions += dataSpec.position
        return bytes.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (failFirstRead && !failed) {
            failed = true
            throw IOException("scripted transport failure")
        }
        if (this.offset == bytes.size) return -1
        val count = minOf(length, bytes.size - this.offset, guard.maximumResponseBytesForRead(length.toLong()).toInt())
        if (count <= 0 || !guard.onResponseBytes(count.toLong())) throw IOException("budget exhausted")
        bytes.copyInto(buffer, offset, this.offset, this.offset + count)
        this.offset += count
        return count
    }

    override fun getUri(): Uri? = Uri.parse("https://cdn.shopify.com/videos/home.mp4")

    override fun close() = Unit
}
