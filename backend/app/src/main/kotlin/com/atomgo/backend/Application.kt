package com.atomgo.backend

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.PaymentType
import com.atomgo.backend.domain.PricingRules
import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.AppUser
import com.atomgo.backend.domain.ClientAccount
import com.atomgo.backend.domain.ClientPhone
import com.atomgo.backend.domain.RentalRecord
import com.atomgo.backend.infra.AuthService
import com.atomgo.backend.infra.InMemoryStore
import com.atomgo.backend.infra.PaymentService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

@Serializable
private data class ApiServiceInfoResponse(
    val service: String,
    val version: String
)

@Serializable
private data class ApiStatusResponse(
    val status: String
)

@Serializable
private data class ApiErrorResponse(
    val message: String
)

@Serializable
private data class ApiAuthLoginResponse(
    @SerialName("access_token")
    val accessToken: String,
    val role: String,
    @SerialName("user_id")
    val userId: String
)

@Serializable
private data class ApiClientPaymentPresetsResponse(
    @SerialName("day_rub")
    val dayRub: Int,
    @SerialName("week_rub")
    val weekRub: Int,
    @SerialName("two_weeks_rub")
    val twoWeeksRub: Int,
    @SerialName("month_rub")
    val monthRub: Int,
    @SerialName("debt_exact_rub")
    val debtExactRub: Int
)

@Serializable
private data class ApiClientDashboardResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("rental_start")
    val rentalStart: String,
    @SerialName("paid_until")
    val paidUntil: String,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int,
    val presets: ApiClientPaymentPresetsResponse
)

@Serializable
private data class ApiAdminClientSummaryResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("status_text")
    val statusText: String,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("profit_rub")
    val profitRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int
)

@Serializable
private data class ApiAdminClientPhoneResponse(
    val label: String,
    val number: String
)

@Serializable
private data class ApiAdminRentalHistoryItemResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("period_start")
    val periodStart: String,
    @SerialName("period_end")
    val periodEnd: String?,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("video_url")
    val videoUrl: String?,
    @SerialName("contract_url")
    val contractUrl: String?,
    val comment: String?
)

@Serializable
private data class ApiAdminClientDetailsResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("full_name")
    val fullName: String,
    val address: String,
    @SerialName("passport_data")
    val passportData: String,
    @SerialName("weekly_rate_rub")
    val weeklyRateRub: Int,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("rental_start")
    val rentalStart: String,
    @SerialName("paid_until")
    val paidUntil: String,
    @SerialName("total_paid_rub")
    val totalPaidRub: Int,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int,
    val phones: List<ApiAdminClientPhoneResponse>,
    val rentals: List<ApiAdminRentalHistoryItemResponse>
)

@Serializable
private data class ApiAdminCreateClientRequest(
    val login: String,
    val password: String,
    @SerialName("full_name")
    val fullName: String,
    val address: String,
    @SerialName("passport_data")
    val passportData: String,
    @SerialName("weekly_rate_rub")
    val weeklyRateRub: Int,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("rental_start")
    val rentalStart: String,
    val phones: List<ApiAdminClientPhoneResponse> = emptyList(),
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null,
    val comment: String? = null
)

@Serializable
private data class ApiAdminDebtAdjustmentRequest(
    @SerialName("amount_rub")
    val amountRub: Int,
    val sign: String,
    val comment: String? = null
)

@Serializable
private data class ApiAdminDebtAdjustmentResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int
)

@Serializable
private data class ApiAdminRentalCommentUpdateRequest(
    val comment: String
)

@Serializable
private data class ApiAdminRentalCommentUpdateResponse(
    @SerialName("rental_id")
    val rentalId: String,
    val comment: String
)

@Serializable
private data class ApiAdminRentalLinksUpdateRequest(
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null
)

@Serializable
private data class ApiAdminRentalLinksUpdateResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null
)

@Serializable
private data class ApiPaymentCreateResponse(
    @SerialName("payment_id")
    val paymentId: String,
    @SerialName("amount_rub")
    val amountRub: Int,
    @SerialName("confirmation_url")
    val confirmationUrl: String,
    @SerialName("idempotence_key")
    val idempotenceKey: String,
    val status: String
)

@Serializable
private data class ApiWebhookApplyResponse(
    val applied: Boolean,
    val message: String,
    @SerialName("payment_id")
    val paymentId: String?,
    @SerialName("client_id")
    val clientId: String?,
    @SerialName("debt_rub")
    val debtRub: Int?
)

private fun buildAdminClientSummary(
    client: ClientAccount,
    store: InMemoryStore,
    now: LocalDate
): ApiAdminClientSummaryResponse {
    val debt = LedgerCalculator.debtRub(client, store.ledger, now)
    val paid = LedgerCalculator.totalPaidRub(store.ledger, client.id)
    val profit = if (debt == 0) paid else 0
    val paidUntil = LedgerCalculator.paidUntilDate(client, store.ledger)
    val dayDiff = ChronoUnit.DAYS.between(now, paidUntil).toInt()
    val statusText = if (dayDiff >= 0) {
        "Оплачено еще на $dayDiff дн."
    } else {
        "Долг за ${-dayDiff} дн."
    }

    return ApiAdminClientSummaryResponse(
        clientId = client.id,
        fullName = client.fullName,
        bikeModel = client.bikeModel,
        bikeAvatarUrl = client.bikeAvatarUrl,
        statusText = statusText,
        debtRub = debt,
        profitRub = profit,
        totalAdjustmentRub = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)
    )
}

private fun buildAdminClientDetails(
    client: ClientAccount,
    store: InMemoryStore,
    now: LocalDate
): ApiAdminClientDetailsResponse {
    val debt = LedgerCalculator.debtRub(client, store.ledger, now)
    val paidUntil = LedgerCalculator.paidUntilDate(client, store.ledger)
    val totalPaid = LedgerCalculator.totalPaidRub(store.ledger, client.id)
    val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)
    val rentals = store.rentals
        .asSequence()
        .filter { it.clientId == client.id }
        .sortedByDescending { it.startDate }
        .map {
            ApiAdminRentalHistoryItemResponse(
                rentalId = it.id,
                bikeAvatarUrl = it.bikeAvatarUrl,
                periodStart = it.startDate.toString(),
                periodEnd = it.endDate?.toString(),
                bikeModel = it.bikeModel,
                videoUrl = it.videoUrl,
                contractUrl = it.contractUrl,
                comment = it.comment
            )
        }
        .toList()

    return ApiAdminClientDetailsResponse(
        clientId = client.id,
        fullName = client.fullName,
        address = client.address,
        passportData = client.passportData,
        weeklyRateRub = client.weeklyRateRub,
        bikeModel = client.bikeModel,
        bikeAvatarUrl = client.bikeAvatarUrl,
        rentalStart = client.rentalStartDate.toString(),
        paidUntil = paidUntil.toString(),
        totalPaidRub = totalPaid,
        debtRub = debt,
        totalAdjustmentRub = totalAdjustment,
        phones = client.phones.map { ApiAdminClientPhoneResponse(label = it.label, number = it.number) },
        rentals = rentals
    )
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    val store = InMemoryStore.seed()
    val authService = AuthService(store)
    val paymentService = PaymentService(store)

    routing {
        get("/") {
            call.respond(ApiServiceInfoResponse(service = "Atom Go API", version = "0.1.0"))
        }
        get("/health/live") {
            call.respond(HttpStatusCode.OK, ApiStatusResponse(status = "live"))
        }
        get("/health/ready") {
            call.respond(HttpStatusCode.OK, ApiStatusResponse(status = "ready"))
        }

        route("/api/v1") {
            post("/auth/login") {
                val request = call.receive<JsonObject>()
                val login = request.string("login")
                val password = request.string("password")

                if (login.isNullOrBlank() || password.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "login and password are required"))
                    return@post
                }

                val auth = authService.login(login, password)
                if (auth == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Неверный логин или пароль"))
                    return@post
                }
                val (token, session) = auth
                call.respond(
                    HttpStatusCode.OK,
                    ApiAuthLoginResponse(
                        accessToken = token,
                        role = authService.roleToApi(session.role),
                        userId = session.userId
                    )
                )
            }

            get("/client/me/dashboard") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.CLIENT || session.clientId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val client = store.clients.firstOrNull { it.id == session.clientId }
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Client not found"))
                    return@get
                }
                val debt = LedgerCalculator.debtRub(client, store.ledger, LocalDate.now())
                val paidUntil = LedgerCalculator.paidUntilDate(client, store.ledger)
                val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)

                call.respond(
                    ApiClientDashboardResponse(
                        clientId = client.id,
                        bikeModel = client.bikeModel,
                        rentalStart = client.rentalStartDate.toString(),
                        paidUntil = paidUntil.toString(),
                        debtRub = debt,
                        totalAdjustmentRub = totalAdjustment,
                        presets = ApiClientPaymentPresetsResponse(
                            dayRub = PricingRules.dayAmount(client.weeklyRateRub),
                            weekRub = PricingRules.weekAmount(client.weeklyRateRub),
                            twoWeeksRub = PricingRules.twoWeeksAmount(client.weeklyRateRub),
                            monthRub = PricingRules.monthAmount(client.weeklyRateRub),
                            debtExactRub = debt
                        )
                    )
                )
            }

            get("/admin/clients") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val now = LocalDate.now()
                val response = store.clients.map { client -> buildAdminClientSummary(client, store, now) }
                call.respond(response)
            }

            get("/admin/clients/{clientId}") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val clientId = call.parameters["clientId"]
                if (clientId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "clientId is required"))
                    return@get
                }

                val client = store.clients.firstOrNull { it.id == clientId }
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Client not found"))
                    return@get
                }

                call.respond(buildAdminClientDetails(client, store, LocalDate.now()))
            }

            post("/admin/clients") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val request = call.receive<ApiAdminCreateClientRequest>()
                val login = request.login.trim()
                val password = request.password.trim()
                val fullName = request.fullName.trim()
                val address = request.address.trim()
                val passportData = request.passportData.trim()
                val bikeModel = request.bikeModel.trim()
                val bikeAvatarUrl = request.bikeAvatarUrl.trim()
                if (login.isBlank() || password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "login and password are required"))
                    return@post
                }
                if (fullName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "full_name is required"))
                    return@post
                }
                if (request.weeklyRateRub <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "weekly_rate_rub must be positive"))
                    return@post
                }
                if (store.users.any { it.login.equals(login, ignoreCase = true) }) {
                    call.respond(HttpStatusCode.Conflict, ApiErrorResponse(message = "login is already used"))
                    return@post
                }

                val rentalStartDate = try {
                    LocalDate.parse(request.rentalStart)
                } catch (_: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "rental_start must be YYYY-MM-DD"))
                    return@post
                }

                val clientId = "client-${UUID.randomUUID().toString().take(8)}"
                val userId = "user-${UUID.randomUUID().toString().take(8)}"
                val normalizedPhones = request.phones
                    .map { ClientPhone(label = it.label.trim(), number = it.number.trim()) }
                    .filter { it.label.isNotBlank() && it.number.isNotBlank() }
                    .toMutableList()

                val client = ClientAccount(
                    id = clientId,
                    fullName = fullName,
                    weeklyRateRub = request.weeklyRateRub,
                    rentalStartDate = rentalStartDate,
                    bikeModel = bikeModel,
                    bikeAvatarUrl = bikeAvatarUrl,
                    address = address,
                    passportData = passportData,
                    phones = normalizedPhones,
                    totalAdjustmentRub = 0
                )
                val user = AppUser(
                    id = userId,
                    login = login,
                    password = password,
                    role = Role.CLIENT,
                    clientId = clientId
                )

                store.clients += client
                store.users += user
                store.rentals += RentalRecord(
                    id = "rental-${UUID.randomUUID().toString().take(8)}",
                    clientId = clientId,
                    bikeAvatarUrl = bikeAvatarUrl,
                    bikeModel = bikeModel,
                    startDate = rentalStartDate,
                    endDate = null,
                    videoUrl = request.videoUrl?.trim()?.ifBlank { null },
                    contractUrl = request.contractUrl?.trim()?.ifBlank { null },
                    comment = request.comment?.trim()?.ifBlank { null }
                )

                call.respond(HttpStatusCode.Created, buildAdminClientDetails(client, store, LocalDate.now()))
            }

            post("/admin/clients/{clientId}/adjustments") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val clientId = call.parameters["clientId"]
                if (clientId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "clientId is required"))
                    return@post
                }

                val client = store.clients.firstOrNull { it.id == clientId }
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Client not found"))
                    return@post
                }

                val request = call.receive<ApiAdminDebtAdjustmentRequest>()
                if (request.amountRub <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "amount_rub must be positive"))
                    return@post
                }
                val normalizedSign = request.sign.trim().lowercase()
                val direction = when (normalizedSign) {
                    "minus" -> -1
                    "plus" -> 1
                    else -> null
                }
                if (direction == null) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "sign must be plus or minus"))
                    return@post
                }

                store.ledger += LedgerEntry(
                    id = "adj-${UUID.randomUUID().toString().take(8)}",
                    clientId = client.id,
                    type = LedgerType.ADJUSTMENT,
                    direction = direction,
                    amountRub = request.amountRub,
                    createdAt = java.time.Instant.now(),
                    note = request.comment?.trim()?.ifBlank { null }
                )

                val debt = LedgerCalculator.debtRub(client, store.ledger, LocalDate.now())
                val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)
                call.respond(
                    ApiAdminDebtAdjustmentResponse(
                        clientId = client.id,
                        debtRub = debt,
                        totalAdjustmentRub = totalAdjustment
                    )
                )
            }

            post("/admin/rentals/{rentalId}/comment") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val rentalId = call.parameters["rentalId"]
                if (rentalId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "rentalId is required"))
                    return@post
                }

                val rental = store.rentals.firstOrNull { it.id == rentalId }
                if (rental == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }

                val request = call.receive<ApiAdminRentalCommentUpdateRequest>()
                val comment = request.comment.trim()
                if (comment.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "comment is required"))
                    return@post
                }

                rental.comment = comment
                call.respond(
                    ApiAdminRentalCommentUpdateResponse(
                        rentalId = rental.id,
                        comment = comment
                    )
                )
            }

            post("/admin/rentals/{rentalId}/links") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val rentalId = call.parameters["rentalId"]
                if (rentalId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "rentalId is required"))
                    return@post
                }

                val rental = store.rentals.firstOrNull { it.id == rentalId }
                if (rental == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }

                val request = call.receive<ApiAdminRentalLinksUpdateRequest>()
                rental.videoUrl = request.videoUrl?.trim()?.ifBlank { null }
                rental.contractUrl = request.contractUrl?.trim()?.ifBlank { null }

                call.respond(
                    ApiAdminRentalLinksUpdateResponse(
                        rentalId = rental.id,
                        videoUrl = rental.videoUrl,
                        contractUrl = rental.contractUrl
                    )
                )
            }

            post("/payments/create") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.CLIENT || session.clientId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val request = call.receive<JsonObject>()
                val type = PaymentType.fromApi(request.string("payment_type") ?: "")
                if (type == null) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "Unknown payment_type"))
                    return@post
                }

                try {
                    val payment = paymentService.createPayment(clientId = session.clientId, paymentType = type)
                    call.respond(
                        HttpStatusCode.OK,
                        ApiPaymentCreateResponse(
                            paymentId = payment.id,
                            amountRub = payment.amountRub,
                            confirmationUrl = payment.confirmationUrl,
                            idempotenceKey = payment.idempotenceKey,
                            status = payment.status.name.lowercase()
                        )
                    )
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = e.message ?: "Invalid payment"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = e.message ?: "Invalid request"))
                }
            }

            post("/payments/yookassa/webhook") {
                val webhook = call.receive<JsonObject>()
                val event = webhook.string("event")
                val obj = webhook["object"]?.jsonObject
                val providerPaymentId = obj?.string("id")
                val metadata = obj?.get("metadata")?.jsonObject
                val localPaymentId = metadata?.string("local_payment_id")

                if (event.isNullOrBlank() || providerPaymentId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "Invalid webhook payload"))
                    return@post
                }

                val result = paymentService.applyWebhook(
                    event = event,
                    providerPaymentId = providerPaymentId,
                    localPaymentId = localPaymentId
                )

                call.respond(
                    HttpStatusCode.OK,
                    ApiWebhookApplyResponse(
                        applied = result.applied,
                        message = result.message,
                        paymentId = result.paymentId,
                        clientId = result.clientId,
                        debtRub = result.debtRub
                    )
                )
            }
        }
    }
}
