package com.atomgo.backend

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.PaymentType
import com.atomgo.backend.domain.PricingRules
import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.AppUser
import com.atomgo.backend.domain.BikeAccount
import com.atomgo.backend.domain.ClientAccount
import com.atomgo.backend.domain.ClientPhone
import com.atomgo.backend.domain.RentalRecord
import com.atomgo.backend.infra.AuthService
import com.atomgo.backend.infra.InMemoryStore
import com.atomgo.backend.infra.PaymentService
import com.atomgo.backend.infra.PostgresStateStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
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
    @SerialName("full_name")
    val fullName: String,
    val address: String,
    @SerialName("passport_data")
    val passportData: String,
    val phones: List<ApiAdminClientPhoneResponse> = emptyList()
)

@Serializable
private data class ApiAdminUpdateClientRequest(
    @SerialName("full_name")
    val fullName: String,
    val address: String,
    @SerialName("passport_data")
    val passportData: String,
    val phones: List<ApiAdminClientPhoneResponse> = emptyList()
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
private data class ApiAdminCreateRentalRequest(
    @SerialName("client_id")
    val clientId: String? = null,
    @SerialName("bike_id")
    val bikeId: String,
    val login: String,
    val password: String,
    @SerialName("period_start")
    val periodStart: String,
    @SerialName("period_end")
    val periodEnd: String? = null,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null,
    val comment: String? = null
)

@Serializable
private data class ApiAdminBikeResponse(
    @SerialName("bike_id")
    val bikeId: String,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("weekly_rate_rub")
    val weeklyRateRub: Int,
    @SerialName("frame_serial_number")
    val frameSerialNumber: String,
    @SerialName("motor_serial_number")
    val motorSerialNumber: String,
    @SerialName("battery_serial_number_1")
    val batterySerialNumber1: String,
    @SerialName("battery_serial_number_2")
    val batterySerialNumber2: String? = null
)

@Serializable
private data class ApiAdminCreateBikeRequest(
    @SerialName("photo_url")
    val photoUrl: String? = null,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("weekly_rate_rub")
    val weeklyRateRub: Int,
    @SerialName("frame_serial_number")
    val frameSerialNumber: String,
    @SerialName("motor_serial_number")
    val motorSerialNumber: String,
    @SerialName("battery_serial_number_1")
    val batterySerialNumber1: String,
    @SerialName("battery_serial_number_2")
    val batterySerialNumber2: String? = null
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

private data class ClientBillingSnapshot(
    val rentalStartDate: LocalDate,
    val weeklyRateRub: Int,
    val bikeModel: String,
    val bikePhotoUrl: String?
)

private fun resolveCurrentRental(clientId: String, store: InMemoryStore, asOf: LocalDate): RentalRecord? {
    val clientRentals = store.rentals
        .asSequence()
        .filter { it.clientId == clientId }
        .sortedByDescending { it.startDate }
        .toList()
    if (clientRentals.isEmpty()) return null

    return clientRentals.firstOrNull { rental ->
        rental.startDate <= asOf && (rental.endDate == null || !rental.endDate.isBefore(asOf))
    } ?: clientRentals.first()
}

private fun resolveClientBillingSnapshot(clientId: String, store: InMemoryStore, asOf: LocalDate): ClientBillingSnapshot? {
    val rental = resolveCurrentRental(clientId = clientId, store = store, asOf = asOf) ?: return null
    val bike = store.bikes.firstOrNull { it.id == rental.bikeId } ?: return null
    return ClientBillingSnapshot(
        rentalStartDate = rental.startDate,
        weeklyRateRub = bike.weeklyRateRub,
        bikeModel = bike.model,
        bikePhotoUrl = bike.photoUrl
    )
}

private fun bikeToApiResponse(bike: BikeAccount): ApiAdminBikeResponse {
    return ApiAdminBikeResponse(
        bikeId = bike.id,
        photoUrl = bike.photoUrl,
        bikeModel = bike.model,
        weeklyRateRub = bike.weeklyRateRub,
        frameSerialNumber = bike.frameSerialNumber,
        motorSerialNumber = bike.motorSerialNumber,
        batterySerialNumber1 = bike.batterySerialNumber1,
        batterySerialNumber2 = bike.batterySerialNumber2
    )
}

private sealed class RentalCreationOutcome {
    data class Success(val rental: ApiAdminRentalHistoryItemResponse) : RentalCreationOutcome()
    data class Failure(val status: HttpStatusCode, val message: String) : RentalCreationOutcome()
}

private fun createRentalForClient(
    store: InMemoryStore,
    stateLock: Any,
    persistState: () -> Unit,
    explicitClientId: String?,
    request: ApiAdminCreateRentalRequest
): RentalCreationOutcome {
    val clientId = (explicitClientId ?: request.clientId)?.trim().orEmpty()
    if (clientId.isBlank()) {
        return RentalCreationOutcome.Failure(HttpStatusCode.BadRequest, "client_id is required")
    }

    val bikeId = request.bikeId.trim()
    val login = request.login.trim()
    val password = request.password.trim()
    val periodStartRaw = request.periodStart.trim()
    val periodEndRaw = request.periodEnd?.trim()?.ifBlank { null }

    if (bikeId.isBlank()) {
        return RentalCreationOutcome.Failure(HttpStatusCode.BadRequest, "bike_id is required")
    }
    if (login.isBlank() || password.isBlank()) {
        return RentalCreationOutcome.Failure(HttpStatusCode.BadRequest, "login and password are required")
    }
    if (periodStartRaw.isBlank()) {
        return RentalCreationOutcome.Failure(HttpStatusCode.BadRequest, "period_start is required")
    }

    val periodStart = try {
        LocalDate.parse(periodStartRaw)
    } catch (_: Throwable) {
        return RentalCreationOutcome.Failure(HttpStatusCode.BadRequest, "period_start must be YYYY-MM-DD")
    }

    val periodEnd = if (periodEndRaw != null) {
        try {
            LocalDate.parse(periodEndRaw)
        } catch (_: Throwable) {
            return RentalCreationOutcome.Failure(HttpStatusCode.BadRequest, "period_end must be YYYY-MM-DD")
        }
    } else {
        null
    }

    if (periodEnd != null && periodEnd.isBefore(periodStart)) {
        return RentalCreationOutcome.Failure(HttpStatusCode.BadRequest, "period_end must be after or equal to period_start")
    }

    val client = store.clients.firstOrNull { it.id == clientId }
        ?: return RentalCreationOutcome.Failure(HttpStatusCode.NotFound, "Client not found")
    val bike = store.bikes.firstOrNull { it.id == bikeId }
        ?: return RentalCreationOutcome.Failure(HttpStatusCode.NotFound, "Bike not found")

    return synchronized(stateLock) {
        val duplicateLogin = store.users.firstOrNull {
            it.login.equals(login, ignoreCase = true) && it.clientId != client.id
        }
        if (duplicateLogin != null) {
            return@synchronized RentalCreationOutcome.Failure(HttpStatusCode.Conflict, "login is already used")
        }

        val existingClientUserIndex = store.users.indexOfFirst { it.clientId == client.id && it.role == Role.CLIENT }
        if (existingClientUserIndex >= 0) {
            val existingClientUser = store.users[existingClientUserIndex]
            store.users[existingClientUserIndex] = existingClientUser.copy(
                login = login,
                password = password
            )
        } else {
            store.users += AppUser(
                id = "user-${UUID.randomUUID().toString().take(8)}",
                login = login,
                password = password,
                role = Role.CLIENT,
                clientId = client.id
            )
        }

        val rental = RentalRecord(
            id = "rental-${UUID.randomUUID().toString().take(8)}",
            clientId = client.id,
            bikeId = bike.id,
            startDate = periodStart,
            endDate = periodEnd,
            videoUrl = request.videoUrl?.trim()?.ifBlank { null },
            contractUrl = request.contractUrl?.trim()?.ifBlank { null },
            comment = request.comment?.trim()?.ifBlank { null }
        )
        store.rentals += rental
        persistState()
        RentalCreationOutcome.Success(
            ApiAdminRentalHistoryItemResponse(
                rentalId = rental.id,
                bikeAvatarUrl = bike.photoUrl ?: "",
                periodStart = rental.startDate.toString(),
                periodEnd = rental.endDate?.toString(),
                bikeModel = bike.model,
                videoUrl = rental.videoUrl,
                contractUrl = rental.contractUrl,
                comment = rental.comment
            )
        )
    }
}

private fun buildAdminClientSummary(
    client: ClientAccount,
    store: InMemoryStore,
    now: LocalDate
): ApiAdminClientSummaryResponse {
    val snapshot = resolveClientBillingSnapshot(client.id, store, now)
    val debt = if (snapshot != null) {
        LedgerCalculator.debtRub(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger,
            asOf = now
        )
    } else {
        0
    }
    val paid = LedgerCalculator.totalPaidRub(store.ledger, client.id)
    val profit = if (debt == 0) paid else 0
    val statusText = if (snapshot == null) {
        "Нет активной аренды"
    } else {
        val paidUntil = LedgerCalculator.paidUntilDate(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger
        )
        val dayDiff = ChronoUnit.DAYS.between(now, paidUntil).toInt()
        if (dayDiff >= 0) {
            "Оплачено еще на $dayDiff дн."
        } else {
            "Долг за ${-dayDiff} дн."
        }
    }

    return ApiAdminClientSummaryResponse(
        clientId = client.id,
        fullName = client.fullName,
        bikeModel = snapshot?.bikeModel ?: "-",
        bikeAvatarUrl = snapshot?.bikePhotoUrl ?: "",
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
    val snapshot = resolveClientBillingSnapshot(client.id, store, now)
    val debt = if (snapshot != null) {
        LedgerCalculator.debtRub(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger,
            asOf = now
        )
    } else {
        0
    }
    val paidUntil = if (snapshot != null) {
        LedgerCalculator.paidUntilDate(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger
        ).toString()
    } else {
        ""
    }
    val totalPaid = LedgerCalculator.totalPaidRub(store.ledger, client.id)
    val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)
    val rentals = store.rentals
        .asSequence()
        .filter { it.clientId == client.id }
        .sortedByDescending { it.startDate }
        .map {
            val bike = store.bikes.firstOrNull { bike -> bike.id == it.bikeId }
            ApiAdminRentalHistoryItemResponse(
                rentalId = it.id,
                bikeAvatarUrl = bike?.photoUrl ?: "",
                periodStart = it.startDate.toString(),
                periodEnd = it.endDate?.toString(),
                bikeModel = bike?.model ?: "-",
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
        weeklyRateRub = snapshot?.weeklyRateRub ?: 0,
        bikeModel = snapshot?.bikeModel ?: "",
        bikeAvatarUrl = snapshot?.bikePhotoUrl ?: "",
        rentalStart = snapshot?.rentalStartDate?.toString() ?: "",
        paidUntil = paidUntil,
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
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is CancellationException -> throw cause
                is BadRequestException, is ContentTransformationException, is SerializationException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorResponse(message = "invalid request body")
                    )
                }
                is IllegalArgumentException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorResponse(message = cause.message ?: "invalid request")
                    )
                }
                else -> {
                    call.application.environment.log.error("Unhandled backend exception", cause)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiErrorResponse(message = "internal server error")
                    )
                }
            }
        }
    }

    val useInMemory = System.getenv("ATOMGO_USE_INMEMORY")?.equals("true", ignoreCase = true) == true
    val stateStore = if (useInMemory) null else PostgresStateStore.fromEnvironment()
    val store = stateStore?.loadOrInitialize(InMemoryStore.seed()) ?: InMemoryStore.seed()
    val stateLock = Any()
    val persistState: () -> Unit = {
        stateStore?.save(store)
    }
    if (useInMemory) {
        println("AtomGo backend storage mode: IN-MEMORY (tests/dev override)")
    } else {
        println("AtomGo backend storage mode: POSTGRESQL")
    }
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

                val auth = synchronized(stateLock) {
                    authService.login(login, password)?.also {
                        persistState()
                    }
                }
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
                val now = LocalDate.now()
                val snapshot = resolveClientBillingSnapshot(client.id, store, now)
                val debt = if (snapshot != null) {
                    LedgerCalculator.debtRub(
                        clientId = client.id,
                        rentalStartDate = snapshot.rentalStartDate,
                        weeklyRateRub = snapshot.weeklyRateRub,
                        entries = store.ledger,
                        asOf = now
                    )
                } else {
                    0
                }
                val paidUntil = if (snapshot != null) {
                    LedgerCalculator.paidUntilDate(
                        clientId = client.id,
                        rentalStartDate = snapshot.rentalStartDate,
                        weeklyRateRub = snapshot.weeklyRateRub,
                        entries = store.ledger
                    ).toString()
                } else {
                    ""
                }
                val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)
                val weeklyRate = snapshot?.weeklyRateRub ?: 0

                call.respond(
                    ApiClientDashboardResponse(
                        clientId = client.id,
                        bikeModel = snapshot?.bikeModel ?: "",
                        rentalStart = snapshot?.rentalStartDate?.toString() ?: "",
                        paidUntil = paidUntil,
                        debtRub = debt,
                        totalAdjustmentRub = totalAdjustment,
                        presets = ApiClientPaymentPresetsResponse(
                            dayRub = PricingRules.dayAmount(weeklyRate),
                            weekRub = PricingRules.weekAmount(weeklyRate),
                            twoWeeksRub = PricingRules.twoWeeksAmount(weeklyRate),
                            monthRub = PricingRules.monthAmount(weeklyRate),
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

            get("/admin/bikes") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }
                call.respond(store.bikes.map { bikeToApiResponse(it) })
            }

            post("/admin/bikes") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val request = call.receive<ApiAdminCreateBikeRequest>()
                val bikeModel = request.bikeModel.trim()
                val frameSerial = request.frameSerialNumber.trim()
                val motorSerial = request.motorSerialNumber.trim()
                val battery1 = request.batterySerialNumber1.trim()
                val battery2 = request.batterySerialNumber2?.trim()?.ifBlank { null }
                val photoUrl = request.photoUrl?.trim()?.ifBlank { null }
                if (bikeModel.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "bike_model is required"))
                    return@post
                }
                if (request.weeklyRateRub <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "weekly_rate_rub must be positive"))
                    return@post
                }
                if (frameSerial.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "frame_serial_number is required"))
                    return@post
                }
                if (motorSerial.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "motor_serial_number is required"))
                    return@post
                }
                if (battery1.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "battery_serial_number_1 is required"))
                    return@post
                }

                val createdBike = synchronized(stateLock) {
                    if (store.bikes.any { it.frameSerialNumber.equals(frameSerial, ignoreCase = true) }) {
                        null
                    } else {
                        val bike = BikeAccount(
                            id = "bike-${UUID.randomUUID().toString().take(8)}",
                            photoUrl = photoUrl,
                            model = bikeModel,
                            weeklyRateRub = request.weeklyRateRub,
                            frameSerialNumber = frameSerial,
                            motorSerialNumber = motorSerial,
                            batterySerialNumber1 = battery1,
                            batterySerialNumber2 = battery2
                        )
                        store.bikes += bike
                        persistState()
                        bike
                    }
                }
                if (createdBike == null) {
                    call.respond(HttpStatusCode.Conflict, ApiErrorResponse(message = "frame_serial_number is already used"))
                    return@post
                }

                call.respond(HttpStatusCode.Created, bikeToApiResponse(createdBike))
            }

            post("/admin/clients") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val request = call.receive<ApiAdminCreateClientRequest>()
                val fullName = request.fullName.trim()
                val address = request.address.trim()
                val passportData = request.passportData.trim()
                if (fullName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "full_name is required"))
                    return@post
                }

                val clientId = "client-${UUID.randomUUID().toString().take(8)}"
                val normalizedPhones = request.phones
                    .map { ClientPhone(label = it.label.trim(), number = it.number.trim()) }
                    .filter { it.label.isNotBlank() && it.number.isNotBlank() }
                    .toMutableList()

                val created = synchronized(stateLock) {
                    val client = ClientAccount(
                        id = clientId,
                        fullName = fullName,
                        address = address,
                        passportData = passportData,
                        phones = normalizedPhones
                    )

                    store.clients += client
                    persistState()
                    buildAdminClientDetails(client, store, LocalDate.now())
                }

                call.respond(HttpStatusCode.Created, created)
            }

            post("/admin/rentals") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val request = call.receive<ApiAdminCreateRentalRequest>()
                when (val result = createRentalForClient(
                    store = store,
                    stateLock = stateLock,
                    persistState = persistState,
                    explicitClientId = null,
                    request = request
                )) {
                    is RentalCreationOutcome.Failure -> call.respond(result.status, ApiErrorResponse(message = result.message))
                    is RentalCreationOutcome.Success -> call.respond(HttpStatusCode.Created, result.rental)
                }
            }

            post("/admin/clients/{clientId}") {
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

                val request = call.receive<ApiAdminUpdateClientRequest>()
                val fullName = request.fullName.trim()
                val address = request.address.trim()
                val passportData = request.passportData.trim()

                if (fullName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "full_name is required"))
                    return@post
                }

                val normalizedPhones = request.phones
                    .map { ClientPhone(label = it.label.trim(), number = it.number.trim()) }
                    .filter { it.label.isNotBlank() && it.number.isNotBlank() }
                    .toMutableList()

                val updated = synchronized(stateLock) {
                    val index = store.clients.indexOfFirst { it.id == clientId }
                    if (index < 0) {
                        null
                    } else {
                        val currentClient = store.clients[index]
                        val updatedClient = currentClient.copy(
                            fullName = fullName,
                            address = address,
                            passportData = passportData,
                            phones = normalizedPhones
                        )
                        store.clients[index] = updatedClient
                        persistState()
                        buildAdminClientDetails(updatedClient, store, LocalDate.now())
                    }
                }

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Client not found"))
                    return@post
                }

                call.respond(HttpStatusCode.OK, updated)
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

                val response = synchronized(stateLock) {
                    store.ledger += LedgerEntry(
                        id = "adj-${UUID.randomUUID().toString().take(8)}",
                        clientId = client.id,
                        type = LedgerType.ADJUSTMENT,
                        direction = direction,
                        amountRub = request.amountRub,
                        createdAt = java.time.Instant.now(),
                        note = request.comment?.trim()?.ifBlank { null }
                    )

                    persistState()
                    val now = LocalDate.now()
                    val snapshot = resolveClientBillingSnapshot(client.id, store, now)
                    val debt = if (snapshot != null) {
                        LedgerCalculator.debtRub(
                            clientId = client.id,
                            rentalStartDate = snapshot.rentalStartDate,
                            weeklyRateRub = snapshot.weeklyRateRub,
                            entries = store.ledger,
                            asOf = now
                        )
                    } else {
                        0
                    }
                    val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)
                    ApiAdminDebtAdjustmentResponse(
                        clientId = client.id,
                        debtRub = debt,
                        totalAdjustmentRub = totalAdjustment
                    )
                }

                call.respond(
                    response
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

                synchronized(stateLock) {
                    rental.comment = comment
                    persistState()
                }
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
                synchronized(stateLock) {
                    rental.videoUrl = request.videoUrl?.trim()?.ifBlank { null }
                    rental.contractUrl = request.contractUrl?.trim()?.ifBlank { null }
                    persistState()
                }

                call.respond(
                    ApiAdminRentalLinksUpdateResponse(
                        rentalId = rental.id,
                        videoUrl = rental.videoUrl,
                        contractUrl = rental.contractUrl
                    )
                )
            }

            post("/admin/clients/{clientId}/rentals") {
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
                val request = call.receive<ApiAdminCreateRentalRequest>()
                when (val result = createRentalForClient(
                    store = store,
                    stateLock = stateLock,
                    persistState = persistState,
                    explicitClientId = clientId,
                    request = request
                )) {
                    is RentalCreationOutcome.Failure -> call.respond(result.status, ApiErrorResponse(message = result.message))
                    is RentalCreationOutcome.Success -> call.respond(HttpStatusCode.Created, result.rental)
                }
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
                    val payment = synchronized(stateLock) {
                        val createdPayment = paymentService.createPayment(clientId = session.clientId, paymentType = type)
                        persistState()
                        createdPayment
                    }
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

                val result = synchronized(stateLock) {
                    val webhookResult = paymentService.applyWebhook(
                        event = event,
                        providerPaymentId = providerPaymentId,
                        localPaymentId = localPaymentId
                    )
                    persistState()
                    webhookResult
                }

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
