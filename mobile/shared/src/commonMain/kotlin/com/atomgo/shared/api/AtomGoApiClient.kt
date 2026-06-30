package com.atomgo.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

class AtomGoApiClient private constructor(
    baseUrl: String,
    private val httpClient: HttpClient
) {
    constructor(baseUrl: String) : this(baseUrl, defaultHttpClient())

    private val apiBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun isServerReachable(): Boolean {
        val healthBaseUrl = if (apiBaseUrl.endsWith("/api/v1")) {
            apiBaseUrl.removeSuffix("/api/v1")
        } else {
            apiBaseUrl
        }

        return try {
            val response = httpClient.get("$healthBaseUrl/health/ready")
            response.status.isSuccess()
        } catch (_: Throwable) {
            false
        }
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun login(login: String, password: String): AuthSession {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/auth/login") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(LoginRequest(login = login, password = password))
            }
        }
        val body = decodeResponse<LoginResponse>(response)
        return AuthSession(
            accessToken = body.accessToken,
            role = body.role,
            userId = body.userId
        )
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchClientDashboard(accessToken: String): ClientDashboardResponse {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/client/me/dashboard") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchAdminClients(accessToken: String): List<AdminClientSummaryResponse> {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/clients") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchAdminRents(accessToken: String): List<AdminClientSummaryResponse> {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/rents") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchAdminClientCatalog(accessToken: String): List<AdminClientSummaryResponse> {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/client-catalog") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchAdminClientDetails(accessToken: String, clientId: String): AdminClientDetailsResponse {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/clients/$clientId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchAdminBikes(accessToken: String): List<AdminBikeResponse> {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/bikes") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun createAdminClient(
        accessToken: String,
        requestBody: AdminCreateClientRequest
    ): AdminClientDetailsResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/clients") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun createAdminBike(
        accessToken: String,
        requestBody: AdminCreateBikeRequest
    ): AdminBikeResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/bikes") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun updateAdminBike(
        accessToken: String,
        bikeId: String,
        requestBody: AdminUpdateBikeRequest
    ): AdminBikeResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/bikes/$bikeId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun deleteAdminBike(
        accessToken: String,
        bikeId: String
    ): AdminDeleteBikeResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/bikes/$bikeId/delete") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun updateAdminClient(
        accessToken: String,
        clientId: String,
        requestBody: AdminUpdateClientRequest
    ): AdminClientDetailsResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/clients/$clientId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun deleteAdminClient(
        accessToken: String,
        clientId: String
    ): AdminDeleteClientResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/clients/$clientId/delete") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun createAdminRental(
        accessToken: String,
        requestBody: AdminCreateRentalRequest
    ): AdminRentalHistoryItemResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun updateAdminRental(
        accessToken: String,
        rentalId: String,
        requestBody: AdminUpdateRentalRequest
    ): AdminRentalHistoryItemResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals/$rentalId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun deleteAdminRental(
        accessToken: String,
        rentalId: String
    ): AdminDeleteRentalResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals/$rentalId/delete") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchAdminRentalDetails(accessToken: String, rentalId: String): AdminRentalDetailsResponse {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/rentals/$rentalId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun adjustAdminClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    ): AdminDebtAdjustmentResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/clients/$clientId/adjustments") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    AdminDebtAdjustmentRequest(
                        amountRub = amountRub,
                        sign = sign,
                        comment = comment
                    )
                )
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun adjustAdminClientRentalDebt(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    ): AdminDebtAdjustmentResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/client-rentals/$clientRentalId/adjustments") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    AdminDebtAdjustmentRequest(
                        amountRub = amountRub,
                        sign = sign,
                        comment = comment
                    )
                )
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun recordAdminClientRentalCashPayment(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        comment: String?
    ): AdminCashPaymentResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/client-rentals/$clientRentalId/cash-payments") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    AdminCashPaymentRequest(
                        amountRub = amountRub,
                        comment = comment
                    )
                )
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun startAdminClientRentalInExisting(
        accessToken: String,
        rentalId: String,
        clientId: String,
        login: String,
        password: String,
        periodStart: String,
        paymentDay: Int
    ): AdminStartClientRentalResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals/$rentalId/client-rentals") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    AdminStartClientRentalRequest(
                        clientId = clientId,
                        login = login,
                        password = password,
                        periodStart = periodStart,
                        paymentDay = paymentDay
                    )
                )
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun updateAdminRentalPipelineStatus(
        accessToken: String,
        rentalId: String,
        pipelineStatus: String
    ): AdminRentalPipelineStatusResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals/$rentalId/pipeline-status") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(AdminRentalPipelineStatusRequest(pipelineStatus = pipelineStatus))
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun finishAdminRental(
        accessToken: String,
        rentalId: String
    ): AdminFinishRentalResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals/$rentalId/finish") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun updateAdminRentalComment(
        accessToken: String,
        rentalId: String,
        comment: String
    ): AdminRentalCommentUpdateResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals/$rentalId/comment") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(AdminRentalCommentUpdateRequest(comment = comment))
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun updateAdminRentalLinks(
        accessToken: String,
        rentalId: String,
        videoUrl: String?,
        contractUrl: String?
    ): AdminRentalLinksUpdateResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/admin/rentals/$rentalId/links") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    AdminRentalLinksUpdateRequest(
                        videoUrl = videoUrl,
                        contractUrl = contractUrl
                    )
                )
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun createPayment(accessToken: String, paymentType: String, amountRub: Int? = null): CreatePaymentResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/payments/create") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(CreatePaymentRequest(paymentType = paymentType, amountRub = amountRub))
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun updateClientReceiptEmail(accessToken: String, email: String): UpdateClientReceiptEmailResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/client/me/receipt-email") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(UpdateClientReceiptEmailRequest(email = email))
            }
        }
        return decodeResponse(response)
    }

    @Throws(AtomGoApiException::class, CancellationException::class)
    suspend fun fetchPaymentStatus(accessToken: String, paymentId: String): PaymentStatusResponse {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/payments/$paymentId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    fun close() {
        httpClient.close()
    }

    private suspend inline fun executeRequest(block: suspend () -> HttpResponse): HttpResponse {
        return try {
            block()
        } catch (error: Throwable) {
            throw AtomGoApiException.Network(error)
        }
    }

    private suspend inline fun <reified T> decodeResponse(response: HttpResponse): T {
        if (!response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            throw AtomGoApiException.Http(
                code = response.status.value,
                payload = responseBody
            )
        }

        return try {
            response.body<T>()
        } catch (error: Throwable) {
            throw AtomGoApiException.InvalidResponse(error)
        }
    }
}

sealed class AtomGoApiException(message: String) : Exception(message) {
    class Http(val code: Int, val payload: String) :
        AtomGoApiException("HTTP $code: $payload")

    class Network(cause: Throwable) :
        AtomGoApiException(cause.message ?: "Network error")

    class InvalidResponse(cause: Throwable) :
        AtomGoApiException(cause.message ?: "Invalid response")
}

private const val REQUEST_TIMEOUT_MILLIS = 12_000L
private const val CONNECT_TIMEOUT_MILLIS = 5_000L
private const val SOCKET_TIMEOUT_MILLIS = 12_000L

private fun defaultHttpClient(): HttpClient {
    return HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                }
            )
        }
        expectSuccess = false
    }
}
