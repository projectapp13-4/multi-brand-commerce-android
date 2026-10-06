package com.gurbakir.storefront

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.network.NetworkTransport
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpNetworkTransport
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

private const val STOREFRONT_HTTP_TIMEOUT_MILLIS = 60_000L

/** Preserves safe read recovery while a sent mutation remains available for cart reconciliation. */
internal class StorefrontNetworkTransport(endpoint: String) : NetworkTransport {
    private val disposed = AtomicBoolean(false)
    private val httpClient = lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(STOREFRONT_HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(STOREFRONT_HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }
    private val mutationCalls = object : Call.Factory {
        override fun newCall(request: Request): Call {
            val body = checkNotNull(request.body) { "Storefront mutation requires an HTTP request body." }
            val oneShotRequest = request.newBuilder()
                .method(request.method, OneShotMutationBody(body))
                .build()
            return httpClient.value.newCall(oneShotRequest)
        }
    }
    private val reads = HttpNetworkTransport.Builder()
        .serverUrl(endpoint)
        .httpEngine(DefaultHttpEngine { httpClient.value })
        .build()
    private val mutations = HttpNetworkTransport.Builder()
        .serverUrl(endpoint)
        .httpEngine(DefaultHttpEngine { mutationCalls })
        .build()

    override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> =
        if (request.operation is Mutation<*>) mutations.execute(request) else reads.execute(request)

    override fun dispose() {
        // Apollo's HTTP subscription fallback may point to this same transport.
        if (!disposed.compareAndSet(false, true)) return
        try {
            reads.dispose()
            mutations.dispose()
        } finally {
            // DefaultHttpEngine does not close an injected client; these resources are private to this transport.
            if (httpClient.isInitialized()) {
                httpClient.value.dispatcher.cancelAll()
                httpClient.value.dispatcher.executorService.shutdown()
                httpClient.value.connectionPool.evictAll()
            }
        }
    }
}

private class OneShotMutationBody(private val delegate: RequestBody) : RequestBody() {
    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun isDuplex() = delegate.isDuplex()

    override fun isOneShot() = true

    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
}
