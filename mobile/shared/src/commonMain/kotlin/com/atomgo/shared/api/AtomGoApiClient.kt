package com.atomgo.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

    suspend fun fetchClientDashboard(accessToken: String): ClientDashboardResponse {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/client/me/dashboard") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    suspend fun fetchAdminClients(accessToken: String): List<AdminClientSummaryResponse> {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/clients") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    suspend fun fetchAdminClientDetails(accessToken: String, clientId: String): AdminClientDetailsResponse {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/clients/$clientId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

    suspend fun fetchAdminBikes(accessToken: String): List<AdminBikeResponse> {
        val response = executeRequest {
            httpClient.get("$apiBaseUrl/admin/bikes") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return decodeResponse(response)
    }

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

    suspend fun createPayment(accessToken: String, paymentType: String): CreatePaymentResponse {
        val response = executeRequest {
            httpClient.post("$apiBaseUrl/payments/create") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(CreatePaymentRequest(paymentType = paymentType))
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

private fun defaultHttpClient(): HttpClient {
    return HttpClient {
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
