package com.gurbakir.account.oauth

import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val HTTP_OK = 200
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_START = 500

sealed interface CustomerLogoutResult {
    data object Success : CustomerLogoutResult

    data class Failure(val reason: CustomerTokenFailure) : CustomerLogoutResult
}

fun interface CustomerAccountLogoutClient {
    suspend fun logout(idToken: SensitiveToken): CustomerLogoutResult
}

class UnconfiguredCustomerAccountLogoutClient : CustomerAccountLogoutClient {
    override suspend fun logout(idToken: SensitiveToken): CustomerLogoutResult =
        CustomerLogoutResult.Failure(CustomerTokenFailure.InvalidResponse)
}

class AndroidCustomerAccountLogoutClient(
    private val configuration: CustomerAccountConfiguration,
    private val discoveryClient: CustomerAccountDiscoveryClient,
    private val httpClient: OkHttpClient = OkHttpClient()
) : CustomerAccountLogoutClient {
    private val requestPlanner = CustomerAccountTokenRequestPlanner(configuration)
    private val noRedirectHttpClient =
        httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()

    init {
        require(configuration.validationIssues().isEmpty()) {
            "Customer Account configuration must be valid before logout."
        }
    }

    override suspend fun logout(idToken: SensitiveToken): CustomerLogoutResult {
        val discovery = discoveryClient.discover()
        if (discovery is CustomerAccountDiscoveryResult.Failure) {
            return CustomerLogoutResult.Failure(discovery.reason.toTokenFailure())
        }
        val requestUri =
            AppAuthCustomerAccountEndSessionRequestFactory.logout(
                configuration,
                requestPlanner.logout(
                    idToken,
                    (discovery as CustomerAccountDiscoveryResult.Success).configuration
                )
            ).toUri()
        val request =
            Request.Builder()
                .url(requestUri.toString())
                .header("Accept", "application/json")
                .get()
                .build()
        return noRedirectHttpClient.newCall(request).awaitLogoutResult()
    }
}

private suspend fun Call.awaitLogoutResult(): CustomerLogoutResult = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWith(
                        Result.success(CustomerLogoutResult.Failure(CustomerTokenFailure.Transient))
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result =
                        when {
                            response.code == HTTP_OK -> CustomerLogoutResult.Success

                            response.code == HTTP_REQUEST_TIMEOUT ||
                                response.code == HTTP_TOO_MANY_REQUESTS ||
                                response.code >= HTTP_SERVER_ERROR_START ->
                                CustomerLogoutResult.Failure(CustomerTokenFailure.Transient)

                            else -> CustomerLogoutResult.Failure(CustomerTokenFailure.Rejected)
                        }
                    if (continuation.isActive) continuation.resumeWith(Result.success(result))
                }
            }
        }
    )
}
