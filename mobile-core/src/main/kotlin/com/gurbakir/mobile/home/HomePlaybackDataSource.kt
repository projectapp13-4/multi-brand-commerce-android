@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.gurbakir.mobile.home

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.gurbakir.storefront.StorefrontMediaClientFactory
import com.gurbakir.storefront.StorefrontMediaLimitExceededException
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontMediaRejectedException
import com.gurbakir.storefront.StorefrontMediaRequestGuard
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

internal fun interface HomePlaybackDelegateFactory {
    fun create(guard: StorefrontMediaRequestGuard): DataSource
}

internal class HomePlaybackDataSourceFactory(
    private val attempt: HomePlaybackAttempt,
    private val rendition: HomeVideoRendition,
    private val delegateFactory: HomePlaybackDelegateFactory
) : DataSource.Factory {
    constructor(
        attempt: HomePlaybackAttempt,
        rendition: HomeVideoRendition,
        mediaPolicy: StorefrontMediaPolicy
    ) : this(
        attempt = attempt,
        rendition = rendition,
        delegateFactory = HomePlaybackDelegateFactory { guard ->
            OkHttpDataSource.Factory(StorefrontMediaClientFactory.create(mediaPolicy, guard)).createDataSource()
        }
    )

    override fun createDataSource(): DataSource = HomePlaybackDataSource(attempt, rendition, delegateFactory)
}

private class HomePlaybackDataSource(
    private val attempt: HomePlaybackAttempt,
    private val rendition: HomeVideoRendition,
    private val delegateFactory: HomePlaybackDelegateFactory
) : DataSource {
    private val transferListeners = CopyOnWriteArrayList<TransferListener>()
    private var delegate: DataSource? = null
    private var originalSpec: DataSpec? = null
    private var retryController: HomePlaybackRetryController? = null
    private var deliveredBytes = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null) { "Data source is already open." }
        deliveredBytes = 0
        originalSpec = dataSpec
        val kind = if (dataSpec.position == 0L) HomePlaybackRequestKind.INITIAL else HomePlaybackRequestKind.RANGE
        val request = attempt.beginRequest(rendition, kind) ?: throw StorefrontMediaRejectedException()
        retryController = HomePlaybackRetryController(attempt, request)
        val guard = HomePlaybackNetworkGuard(attempt, request)

        return try {
            openDelegate(dataSpec, guard)
        } catch (exception: IOException) {
            if (retryController?.shouldRetry(exception) == true) {
                closeDelegate()
                try {
                    openDelegate(dataSpec, guard)
                } catch (retryFailure: IOException) {
                    rejectAfterTerminalFailure(retryFailure)
                    throw retryFailure
                }
            } else {
                rejectAfterTerminalFailure(exception)
                throw exception
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val activeDelegate = checkNotNull(delegate) { "Data source is not open." }
        return try {
            activeDelegate.read(buffer, offset, length).also(::recordDeliveredBytes)
        } catch (exception: IOException) {
            if (retryController?.shouldRetry(exception) != true) {
                rejectAfterTerminalFailure(exception)
                throw exception
            }
            val retrySpec = checkNotNull(originalSpec).subrange(deliveredBytes)
            val request = checkNotNull(retryController).requestToken
            val guard = HomePlaybackNetworkGuard(attempt, request)
            closeDelegate()
            try {
                openDelegate(retrySpec, guard)
                checkNotNull(delegate).read(buffer, offset, length).also(::recordDeliveredBytes)
            } catch (retryFailure: IOException) {
                rejectAfterTerminalFailure(retryFailure)
                throw retryFailure
            }
        }
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        closeDelegate()
        originalSpec = null
        retryController = null
        deliveredBytes = 0
    }

    private fun openDelegate(dataSpec: DataSpec, guard: HomePlaybackNetworkGuard): Long {
        val next = delegateFactory.create(guard)
        transferListeners.forEach(next::addTransferListener)
        delegate = next
        return next.open(dataSpec)
    }

    private fun closeDelegate() {
        val closing = delegate
        delegate = null
        closing?.close()
    }

    private fun recordDeliveredBytes(read: Int) {
        if (read > 0) deliveredBytes += read
    }

    private fun rejectAfterTerminalFailure(exception: IOException) {
        if (exception.hasPlaybackCancellationCause()) return
        when (exception) {
            is StorefrontMediaLimitExceededException -> attempt.terminate(HomePlaybackTerminalReason.BYTE_BUDGET)
            is StorefrontMediaRejectedException -> attempt.rejectRendition(rendition, HomeRenditionRejection.ORIGIN)
            else -> attempt.rejectRendition(rendition, HomeRenditionRejection.TERMINAL_PLAYBACK)
        }
    }
}
