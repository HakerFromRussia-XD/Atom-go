package com.atomgo.backend

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.PaymentType
import com.atomgo.backend.domain.PricingRules
import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.AppUser
import com.atomgo.backend.domain.AdminTaxMode
import com.atomgo.backend.domain.BikeAccount
import com.atomgo.backend.domain.ClientAccount
import com.atomgo.backend.domain.ClientPhone
import com.atomgo.backend.domain.RentalRecord
import com.atomgo.backend.infra.AuthService
import com.atomgo.backend.infra.FiscalizationConfig
import com.atomgo.backend.infra.InMemoryStore
import com.atomgo.backend.infra.PaymentService
import com.atomgo.backend.infra.PostgresStateStore
import com.atomgo.backend.infra.YooKassaException
import com.atomgo.backend.infra.YooKassaPaymentProvider
import com.atomgo.backend.infra.decimalStringToRub
import com.atomgo.backend.infra.mapStatus
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
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
import io.ktor.server.response.respondText
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
    @SerialName("balance_rub")
    val balanceRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int,
    val presets: ApiClientPaymentPresetsResponse,
    @SerialName("tax_mode")
    val taxMode: String,
    @SerialName("requires_receipt_email")
    val requiresReceiptEmail: Boolean
)

@Serializable
private data class ApiAdminClientSummaryResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_login")
    val clientLogin: String? = null,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("status_text")
    val statusText: String,
    @SerialName("paid_until")
    val paidUntil: String? = null,
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
    @SerialName("bike_id")
    val bikeId: String,
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
    val comment: String?,
    @SerialName("admin_id")
    val adminId: String?,
    @SerialName("tax_mode")
    val taxMode: String
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
private data class ApiAdminUpdateBikeRequest(
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
private data class ApiAdminUpdateRentalRequest(
    @SerialName("bike_id")
    val bikeId: String,
    @SerialName("period_start")
    val periodStart: String,
    @SerialName("period_end")
    val periodEnd: String? = null
)

@Serializable
private data class ApiAdminDeleteRentalResponse(
    @SerialName("rental_id")
    val rentalId: String,
    val deleted: Boolean
)

@Serializable
private data class ApiAdminDeleteClientResponse(
    @SerialName("client_id")
    val clientId: String,
    val deleted: Boolean
)

@Serializable
private data class ApiAdminDeleteBikeResponse(
    @SerialName("bike_id")
    val bikeId: String,
    val deleted: Boolean
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
    @SerialName("tax_mode")
    val taxMode: String,
    @SerialName("fiscalization_status")
    val fiscalizationStatus: String,
    val status: String
)

@Serializable
private data class ApiPaymentStatusResponse(
    @SerialName("payment_id")
    val paymentId: String,
    @SerialName("amount_rub")
    val amountRub: Int,
    @SerialName("confirmation_url")
    val confirmationUrl: String,
    @SerialName("provider_payment_id")
    val providerPaymentId: String?,
    val status: String,
    @SerialName("tax_mode")
    val taxMode: String,
    @SerialName("fiscalization_status")
    val fiscalizationStatus: String,
    @SerialName("debt_rub")
    val debtRub: Int?
)

@Serializable
private data class ApiPaymentCreateRequest(
    @SerialName("payment_type")
    val paymentType: String
)

@Serializable
private data class ApiClientReceiptEmailRequest(
    val email: String
)

@Serializable
private data class ApiClientReceiptEmailResponse(
    @SerialName("client_id")
    val clientId: String,
    val email: String
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
    val rentalId: String,
    val rentalStartDate: LocalDate,
    val weeklyRateRub: Int,
    val bikeModel: String,
    val bikePhotoUrl: String?,
    val taxMode: AdminTaxMode
)

private fun resolveCurrentRental(
    clientId: String,
    store: InMemoryStore,
    asOf: LocalDate,
    adminId: String? = null
): RentalRecord? {
    val clientRentals = store.rentals
        .asSequence()
        .filter { it.clientId == clientId }
        .filter { adminId == null || it.adminId == adminId }
        .sortedByDescending { it.startDate }
        .toList()
    if (clientRentals.isEmpty()) return null

    return clientRentals.firstOrNull { rental ->
        rental.startDate <= asOf && (rental.endDate == null || !rental.endDate.isBefore(asOf))
    } ?: clientRentals.first()
}

private fun resolveClientBillingSnapshot(
    clientId: String,
    store: InMemoryStore,
    asOf: LocalDate,
    adminId: String? = null
): ClientBillingSnapshot? {
    val rental = resolveCurrentRental(clientId = clientId, store = store, asOf = asOf, adminId = adminId) ?: return null
    val bike = store.bikes.firstOrNull { it.id == rental.bikeId } ?: return null
    return ClientBillingSnapshot(
        rentalId = rental.id,
        rentalStartDate = rental.startDate,
        weeklyRateRub = bike.weeklyRateRub,
        bikeModel = bike.model,
        bikePhotoUrl = bike.photoUrl,
        taxMode = rental.taxMode
    )
}

private fun clientHasReceiptEmail(client: ClientAccount): Boolean {
    return client.phones.any { normalizeReceiptEmail(it.number) != null }
}

private fun normalizeReceiptEmail(rawEmail: String?): String? {
    val email = rawEmail?.trim()?.lowercase().orEmpty()
    if (email.isBlank() || email.length > 254 || !email.contains("@")) return null
    val parts = email.split("@")
    if (parts.size != 2 || parts.any { it.isBlank() }) return null
    if (!parts[1].contains(".")) return null
    return email
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

private fun bikeContainsSerial(bike: BikeAccount, serial: String): Boolean {
    return bike.frameSerialNumber.equals(serial, ignoreCase = true) ||
        bike.motorSerialNumber.equals(serial, ignoreCase = true) ||
        bike.batterySerialNumber1.equals(serial, ignoreCase = true) ||
        (bike.batterySerialNumber2?.equals(serial, ignoreCase = true) == true)
}

private fun adminOwnsClient(store: InMemoryStore, adminId: String, clientId: String): Boolean {
    val client = store.clients.firstOrNull { it.id == clientId } ?: return false
    return client.adminId == adminId || store.rentals.any { it.clientId == clientId && it.adminId == adminId }
}

private fun adminOwnsBike(store: InMemoryStore, adminId: String, bikeId: String): Boolean {
    val bike = store.bikes.firstOrNull { it.id == bikeId } ?: return false
    return bike.adminId == adminId || store.rentals.any { it.bikeId == bikeId && it.adminId == adminId }
}

private fun currentAdminTaxMode(store: InMemoryStore, adminId: String): AdminTaxMode {
    return store.users.firstOrNull { it.id == adminId && it.role == Role.ADMIN }?.taxMode
        ?: AdminTaxMode.SELF_EMPLOYED
}

private fun ensureDefaultAdminsAndOwnership(store: InMemoryStore): Boolean {
    var changed = false
    val selfEmployedAdminId = "admin-001"
    val ipAdminId = "admin-ip-001"

    fun upsertAdmin(id: String, login: String, password: String, taxMode: AdminTaxMode) {
        val index = store.users.indexOfFirst { it.id == id }
        if (index >= 0) {
            val user = store.users[index]
            if (user.role != Role.ADMIN || user.taxMode != taxMode || user.login != login || user.password != password) {
                store.users[index] = user.copy(login = login, password = password, role = Role.ADMIN, clientId = null, taxMode = taxMode)
                changed = true
            }
            return
        }
        if (store.users.none { it.login == login }) {
            store.users += AppUser(
                id = id,
                login = login,
                password = password,
                role = Role.ADMIN,
                clientId = null,
                taxMode = taxMode
            )
            changed = true
        }
    }

    upsertAdmin(selfEmployedAdminId, "admin", "admin123", AdminTaxMode.SELF_EMPLOYED)
    upsertAdmin(ipAdminId, "admin_ip", "adminip123", AdminTaxMode.INDIVIDUAL_ENTREPRENEUR)

    store.clients.replaceAll { client ->
        if (client.adminId == null) {
            changed = true
            client.copy(adminId = selfEmployedAdminId)
        } else {
            client
        }
    }
    store.bikes.replaceAll { bike ->
        if (bike.adminId == null) {
            changed = true
            bike.copy(adminId = selfEmployedAdminId)
        } else {
            bike
        }
    }
    store.rentals.replaceAll { rental ->
        val nextAdminId = rental.adminId ?: selfEmployedAdminId
        val nextTaxMode = if (nextAdminId == selfEmployedAdminId) AdminTaxMode.SELF_EMPLOYED else rental.taxMode
        if (rental.adminId != nextAdminId || rental.taxMode != nextTaxMode) {
            changed = true
            rental.copy(adminId = nextAdminId, taxMode = nextTaxMode)
        } else {
            rental
        }
    }

    return changed
}

private fun validateUniqueBikeSerials(
    store: InMemoryStore,
    adminId: String?,
    bikeIdToIgnore: String?,
    frameSerial: String,
    motorSerial: String,
    battery1: String,
    battery2: String?
): String? {
    val serialPairs = mutableListOf(
        "frame_serial_number" to frameSerial,
        "motor_serial_number" to motorSerial,
        "battery_serial_number_1" to battery1
    )
    if (battery2 != null) {
        serialPairs += "battery_serial_number_2" to battery2
    }

    val normalized = serialPairs.map { it.second.lowercase() }
    if (normalized.size != normalized.toSet().size) {
        return "serial numbers must be unique inside bike"
    }

    for ((fieldName, serialValue) in serialPairs) {
        val duplicate = store.bikes.firstOrNull { bike ->
            bike.id != bikeIdToIgnore &&
                (adminId == null || bike.adminId == adminId) &&
                bikeContainsSerial(bike, serialValue)
        }
        if (duplicate != null) {
            return "$fieldName is already used"
        }
    }

    return null
}

private fun clientLoginByClientId(store: InMemoryStore, clientId: String): String? {
    return store.users.firstOrNull { it.role == Role.CLIENT && it.clientId == clientId }?.login
}

private sealed class RentalCreationOutcome {
    data class Success(val rental: ApiAdminRentalHistoryItemResponse) : RentalCreationOutcome()
    data class Failure(val status: HttpStatusCode, val message: String) : RentalCreationOutcome()
}

private fun createRentalForClient(
    store: InMemoryStore,
    stateLock: Any,
    persistState: () -> Unit,
    adminId: String,
    taxMode: AdminTaxMode,
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
    if (!adminOwnsClient(store, adminId, client.id)) {
        return RentalCreationOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
    }
    if (!adminOwnsBike(store, adminId, bike.id)) {
        return RentalCreationOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
    }

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
            comment = request.comment?.trim()?.ifBlank { null },
            adminId = adminId,
            taxMode = taxMode
        )
        store.rentals += rental
        persistState()
        RentalCreationOutcome.Success(
            ApiAdminRentalHistoryItemResponse(
                rentalId = rental.id,
                bikeId = bike.id,
                bikeAvatarUrl = bike.photoUrl ?: "",
                periodStart = rental.startDate.toString(),
                periodEnd = rental.endDate?.toString(),
                bikeModel = bike.model,
                videoUrl = rental.videoUrl,
                contractUrl = rental.contractUrl,
                comment = rental.comment,
                adminId = rental.adminId,
                taxMode = rental.taxMode.name.lowercase()
            )
        )
    }
}

private fun buildAdminClientSummary(
    client: ClientAccount,
    store: InMemoryStore,
    now: LocalDate,
    adminId: String? = null
): ApiAdminClientSummaryResponse {
    val snapshot = resolveClientBillingSnapshot(client.id, store, now, adminId)
    val projection = if (snapshot != null) {
        LedgerCalculator.billingProjection(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger,
            asOf = now,
            rentalId = snapshot.rentalId
        )
    } else {
        null
    }
    val debt = projection?.debtRub ?: 0
    val paid = LedgerCalculator.totalPaidRub(store.ledger, client.id, snapshot?.rentalId)
    val profit = if (debt == 0) paid else 0
    val paidUntil = projection?.paidUntilDate
    val statusText = projection?.statusText ?: "Нет активной аренды"

    return ApiAdminClientSummaryResponse(
        clientId = client.id,
        clientLogin = clientLoginByClientId(store, client.id),
        fullName = client.fullName,
        bikeModel = snapshot?.bikeModel ?: "-",
        bikeAvatarUrl = snapshot?.bikePhotoUrl ?: "",
        statusText = statusText,
        paidUntil = paidUntil?.toString(),
        debtRub = debt,
        profitRub = profit,
        totalAdjustmentRub = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.rentalId)
    )
}

private fun buildAdminClientDetails(
    client: ClientAccount,
    store: InMemoryStore,
    now: LocalDate,
    adminId: String? = null
): ApiAdminClientDetailsResponse {
    val snapshot = resolveClientBillingSnapshot(client.id, store, now, adminId)
    val projection = if (snapshot != null) {
        LedgerCalculator.billingProjection(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger,
            asOf = now,
            rentalId = snapshot.rentalId
        )
    } else {
        null
    }
    val debt = projection?.debtRub ?: 0
    val paidUntil = projection?.paidUntilDate?.toString() ?: ""
    val totalPaid = LedgerCalculator.totalPaidRub(store.ledger, client.id, snapshot?.rentalId)
    val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.rentalId)
    val rentals = store.rentals
        .asSequence()
        .filter { it.clientId == client.id }
        .filter { adminId == null || it.adminId == adminId }
        .sortedByDescending { it.startDate }
        .map {
            val bike = store.bikes.firstOrNull { bike -> bike.id == it.bikeId }
            ApiAdminRentalHistoryItemResponse(
                rentalId = it.id,
                bikeId = it.bikeId,
                bikeAvatarUrl = bike?.photoUrl ?: "",
                periodStart = it.startDate.toString(),
                periodEnd = it.endDate?.toString(),
                bikeModel = bike?.model ?: "-",
                videoUrl = it.videoUrl,
                contractUrl = it.contractUrl,
                comment = it.comment,
                adminId = it.adminId,
                taxMode = it.taxMode.name.lowercase()
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
    val apiJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }
    install(ContentNegotiation) {
        json(apiJson)
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
    if (ensureDefaultAdminsAndOwnership(store)) {
        persistState()
    }
    if (useInMemory) {
        println("AtomGo backend storage mode: IN-MEMORY (tests/dev override)")
    } else {
        println("AtomGo backend storage mode: POSTGRESQL")
    }
    val authService = AuthService(store)
    val fiscalizationConfig = FiscalizationConfig.fromEnvironment()
    val paymentService = PaymentService(
        store = store,
        provider = YooKassaPaymentProvider.fromEnvironment(apiJson),
        fiscalizationConfig = fiscalizationConfig
    )

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
                val projection = if (snapshot != null) {
                    LedgerCalculator.billingProjection(
                        clientId = client.id,
                        rentalStartDate = snapshot.rentalStartDate,
                        weeklyRateRub = snapshot.weeklyRateRub,
                        entries = store.ledger,
                        asOf = now,
                        rentalId = snapshot.rentalId
                    )
                } else {
                    null
                }
                val debt = projection?.debtRub ?: 0
                val paidUntil = projection?.paidUntilDate?.toString() ?: ""
                val balanceRub = projection?.balanceRub ?: 0
                val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.rentalId)
                val weeklyRate = snapshot?.weeklyRateRub ?: 0

                call.respond(
                    ApiClientDashboardResponse(
                        clientId = client.id,
                        bikeModel = snapshot?.bikeModel ?: "",
                        rentalStart = snapshot?.rentalStartDate?.toString() ?: "",
                        paidUntil = paidUntil,
                        debtRub = debt,
                        balanceRub = balanceRub,
                        totalAdjustmentRub = totalAdjustment,
                        presets = ApiClientPaymentPresetsResponse(
                            dayRub = PricingRules.dayAmount(weeklyRate),
                            weekRub = PricingRules.weekAmount(weeklyRate),
                            twoWeeksRub = PricingRules.twoWeeksAmount(weeklyRate),
                            monthRub = PricingRules.monthAmount(weeklyRate),
                            debtExactRub = debt
                        ),
                        taxMode = (snapshot?.taxMode ?: AdminTaxMode.SELF_EMPLOYED).name.lowercase(),
                        requiresReceiptEmail = snapshot?.taxMode == AdminTaxMode.INDIVIDUAL_ENTREPRENEUR &&
                            !clientHasReceiptEmail(client)
                    )
                )
            }

            post("/client/me/receipt-email") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.CLIENT || session.clientId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val request = call.receive<ApiClientReceiptEmailRequest>()
                val email = normalizeReceiptEmail(request.email)
                if (email == null) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "client email is invalid"))
                    return@post
                }

                val updated = synchronized(stateLock) {
                    val client = store.clients.firstOrNull { it.id == session.clientId }
                        ?: return@synchronized false
                    if (client.phones.none { normalizeReceiptEmail(it.number) == email }) {
                        client.phones.removeAll { it.label.equals("Email", ignoreCase = true) && normalizeReceiptEmail(it.number) != null }
                        client.phones += ClientPhone(label = "Email", number = email)
                        persistState()
                    }
                    true
                }
                if (!updated) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Client not found"))
                    return@post
                }
                call.respond(HttpStatusCode.OK, ApiClientReceiptEmailResponse(clientId = session.clientId, email = email))
            }

            get("/admin/clients") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val now = LocalDate.now()
                val response = store.clients
                    .filter { client -> adminOwnsClient(store, session.userId, client.id) }
                    .map { client -> buildAdminClientSummary(client, store, now, session.userId) }
                call.respond(response)
            }

            get("/admin/rents") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val now = LocalDate.now()
                val response = store.clients
                    .filter { client ->
                        store.rentals.any { rental ->
                            rental.adminId == session.userId && rental.clientId == client.id
                        }
                    }
                    .map { client -> buildAdminClientSummary(client, store, now, session.userId) }
                call.respond(response)
            }

            get("/admin/client-catalog") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val now = LocalDate.now()
                val response = store.clients
                    .filter { client -> adminOwnsClient(store, session.userId, client.id) }
                    .map { client -> buildAdminClientSummary(client, store, now, session.userId) }
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
                if (!adminOwnsClient(store, session.userId, client.id)) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
                    return@get
                }

                call.respond(buildAdminClientDetails(client, store, LocalDate.now(), session.userId))
            }

            get("/admin/bikes") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }
                call.respond(store.bikes.filter { it.adminId == session.userId }.map { bikeToApiResponse(it) })
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
                    val duplicateError = validateUniqueBikeSerials(
                        store = store,
                        adminId = session.userId,
                        bikeIdToIgnore = null,
                        frameSerial = frameSerial,
                        motorSerial = motorSerial,
                        battery1 = battery1,
                        battery2 = battery2
                    )
                    if (duplicateError != null) {
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
                            batterySerialNumber2 = battery2,
                            adminId = session.userId
                        )
                        store.bikes += bike
                        persistState()
                        bike
                    }
                }
                if (createdBike == null) {
                    val duplicateError = validateUniqueBikeSerials(
                        store = store,
                        adminId = session.userId,
                        bikeIdToIgnore = null,
                        frameSerial = frameSerial,
                        motorSerial = motorSerial,
                        battery1 = battery1,
                        battery2 = battery2
                    ) ?: "bike serial numbers are already used"
                    call.respond(HttpStatusCode.Conflict, ApiErrorResponse(message = duplicateError))
                    return@post
                }

                call.respond(HttpStatusCode.Created, bikeToApiResponse(createdBike))
            }

            post("/admin/bikes/{bikeId}") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val bikeId = call.parameters["bikeId"]
                if (bikeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "bikeId is required"))
                    return@post
                }

                val request = call.receive<ApiAdminUpdateBikeRequest>()
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

                val duplicateError = synchronized(stateLock) {
                    validateUniqueBikeSerials(
                        store = store,
                        adminId = session.userId,
                        bikeIdToIgnore = bikeId,
                        frameSerial = frameSerial,
                        motorSerial = motorSerial,
                        battery1 = battery1,
                        battery2 = battery2
                    )
                }
                if (duplicateError != null) {
                    call.respond(HttpStatusCode.Conflict, ApiErrorResponse(message = duplicateError))
                    return@post
                }

                val updated = synchronized(stateLock) {
                    val bikeIndex = store.bikes.indexOfFirst { it.id == bikeId }
                    if (bikeIndex < 0) {
                        null
                    } else {
                        val currentBike = store.bikes[bikeIndex]
                        if (currentBike.adminId != session.userId) {
                            return@synchronized null
                        }
                        val updatedBike = currentBike.copy(
                            photoUrl = photoUrl,
                            model = bikeModel,
                            weeklyRateRub = request.weeklyRateRub,
                            frameSerialNumber = frameSerial,
                            motorSerialNumber = motorSerial,
                            batterySerialNumber1 = battery1,
                            batterySerialNumber2 = battery2
                        )
                        store.bikes[bikeIndex] = updatedBike
                        persistState()
                        updatedBike
                    }
                }

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Bike not found"))
                    return@post
                }

                call.respond(HttpStatusCode.OK, bikeToApiResponse(updated))
            }

            post("/admin/bikes/{bikeId}/delete") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val bikeId = call.parameters["bikeId"]
                if (bikeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "bikeId is required"))
                    return@post
                }

                val result = synchronized(stateLock) {
                    val bikeIndex = store.bikes.indexOfFirst { it.id == bikeId }
                    if (bikeIndex < 0) {
                        HttpStatusCode.NotFound to "Bike not found"
                    } else {
                        val bike = store.bikes[bikeIndex]
                        if (bike.adminId != session.userId) {
                            HttpStatusCode.NotFound to "Bike not found"
                        } else if (store.rentals.any { it.bikeId == bikeId && it.adminId == session.userId }) {
                            HttpStatusCode.Conflict to "bike is used by rentals"
                        } else {
                            store.bikes.removeAt(bikeIndex)
                            persistState()
                            null
                        }
                    }
                }

                if (result != null) {
                    call.respond(result.first, ApiErrorResponse(message = result.second))
                    return@post
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiAdminDeleteBikeResponse(
                        bikeId = bikeId,
                        deleted = true
                    )
                )
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
	                        phones = normalizedPhones,
	                        adminId = session.userId
	                    )

	                    store.clients += client
	                    persistState()
	                    buildAdminClientDetails(client, store, LocalDate.now(), session.userId)
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
                    adminId = session.userId,
	                    taxMode = currentAdminTaxMode(store, session.userId),
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
	                        if (!adminOwnsClient(store, session.userId, currentClient.id)) {
	                            return@synchronized null
	                        }
	                        val updatedClient = currentClient.copy(
                            fullName = fullName,
                            address = address,
                            passportData = passportData,
                            phones = normalizedPhones
                        )
                        store.clients[index] = updatedClient
                        persistState()
	                        buildAdminClientDetails(updatedClient, store, LocalDate.now(), session.userId)
                    }
                }

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Client not found"))
                    return@post
                }

                call.respond(HttpStatusCode.OK, updated)
            }

            post("/admin/clients/{clientId}/delete") {
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

                val deleted = synchronized(stateLock) {
                    val clientIndex = store.clients.indexOfFirst { it.id == clientId }
                    if (clientIndex < 0) {
                        HttpStatusCode.NotFound to "Client not found"
                    } else {
                        val client = store.clients[clientIndex]
                        if (!adminOwnsClient(store, session.userId, client.id)) {
                            return@synchronized HttpStatusCode.NotFound to "Client not found"
                        }
                        if (store.rentals.any { it.clientId == clientId && it.adminId == session.userId }) {
                            return@synchronized HttpStatusCode.Conflict to "client is used by rentals"
                        }
                        store.clients.removeAt(clientIndex)
                        store.users.removeAll { it.clientId == clientId }
                        store.ledger.removeAll { it.clientId == clientId }
                        store.payments.removeAll { it.clientId == clientId }
                        store.sessions.entries.removeAll { it.value.clientId == clientId }
                        persistState()
                        null
                    }
                }

                if (deleted != null) {
                    call.respond(deleted.first, ApiErrorResponse(message = deleted.second))
                    return@post
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiAdminDeleteClientResponse(
                        clientId = clientId,
                        deleted = true
                    )
                )
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
                if (!adminOwnsClient(store, session.userId, client.id)) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
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
                    val snapshot = resolveClientBillingSnapshot(client.id, store, now, session.userId)
                    val debt = if (snapshot != null) {
                        LedgerCalculator.debtRub(
                            clientId = client.id,
                            rentalStartDate = snapshot.rentalStartDate,
                            weeklyRateRub = snapshot.weeklyRateRub,
                            entries = store.ledger,
                            asOf = now,
                            rentalId = snapshot.rentalId
                        )
                    } else {
                        0
                    }
                    val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.rentalId)
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
                if (rental.adminId != session.userId) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
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
                if (rental.adminId != session.userId) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
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

            post("/admin/rentals/{rentalId}") {
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

                val request = call.receive<ApiAdminUpdateRentalRequest>()
                val bikeId = request.bikeId.trim()
                val periodStartRaw = request.periodStart.trim()
                val periodEndRaw = request.periodEnd?.trim()?.ifBlank { null }

                if (bikeId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "bike_id is required"))
                    return@post
                }
                if (periodStartRaw.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "period_start is required"))
                    return@post
                }

                val periodStart = try {
                    LocalDate.parse(periodStartRaw)
                } catch (_: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "period_start must be YYYY-MM-DD"))
                    return@post
                }

                val periodEnd = if (periodEndRaw != null) {
                    try {
                        LocalDate.parse(periodEndRaw)
                    } catch (_: Throwable) {
                        call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "period_end must be YYYY-MM-DD"))
                        return@post
                    }
                } else {
                    null
                }

                if (periodEnd != null && periodEnd.isBefore(periodStart)) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "period_end must be after or equal to period_start"))
                    return@post
                }

	                val updatedRental = synchronized(stateLock) {
	                    val rentalIndex = store.rentals.indexOfFirst { it.id == rentalId }
	                    if (rentalIndex < 0) {
	                        null
	                    } else {
	                        val bike = store.bikes.firstOrNull { it.id == bikeId }
	                            ?: return@synchronized RentalCreationOutcome.Failure(HttpStatusCode.NotFound, "Bike not found")
	                        val currentRental = store.rentals[rentalIndex]
	                        if (currentRental.adminId != session.userId || !adminOwnsBike(store, session.userId, bike.id)) {
	                            return@synchronized RentalCreationOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
	                        }
                        val updated = currentRental.copy(
                            bikeId = bike.id,
                            startDate = periodStart,
                            endDate = periodEnd
                        )
                        store.rentals[rentalIndex] = updated
                        persistState()
                        ApiAdminRentalHistoryItemResponse(
                            rentalId = updated.id,
                            bikeId = bike.id,
                            bikeAvatarUrl = bike.photoUrl ?: "",
                            periodStart = updated.startDate.toString(),
                            periodEnd = updated.endDate?.toString(),
                            bikeModel = bike.model,
                            videoUrl = updated.videoUrl,
                            contractUrl = updated.contractUrl,
                            comment = updated.comment,
                            adminId = updated.adminId,
                            taxMode = updated.taxMode.name.lowercase()
                        )
                    }
                }

                when (updatedRental) {
                    null -> call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    is RentalCreationOutcome.Failure -> call.respond(updatedRental.status, ApiErrorResponse(message = updatedRental.message))
                    is ApiAdminRentalHistoryItemResponse -> call.respond(HttpStatusCode.OK, updatedRental)
                    else -> call.respond(HttpStatusCode.InternalServerError, ApiErrorResponse(message = "internal server error"))
                }
            }

            post("/admin/rentals/{rentalId}/delete") {
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

	                val deleted = synchronized(stateLock) {
	                    val index = store.rentals.indexOfFirst { it.id == rentalId }
	                    if (index < 0) {
	                        false
	                    } else {
	                        if (store.rentals[index].adminId != session.userId) {
	                            return@synchronized false
	                        }
	                        store.rentals.removeAt(index)
                        persistState()
                        true
                    }
                }

                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiAdminDeleteRentalResponse(
                        rentalId = rentalId,
                        deleted = true
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
                    adminId = session.userId,
	                    taxMode = currentAdminTaxMode(store, session.userId),
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

                val request = call.receive<ApiPaymentCreateRequest>()
                val type = PaymentType.fromApi(request.paymentType)
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
                            taxMode = payment.taxMode.name.lowercase(),
                            fiscalizationStatus = payment.fiscalizationStatus.name.lowercase(),
                            status = payment.status.name.lowercase()
                        )
                    )
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = e.message ?: "Invalid payment"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = e.message ?: "Invalid request"))
                } catch (e: YooKassaException) {
                    call.application.environment.log.error("YooKassa create payment failed", e)
                    val message = if (e.statusCode == 0) {
                        "YooKassa is not configured"
                    } else {
                        "YooKassa payment creation failed"
                    }
                    call.respond(HttpStatusCode.BadGateway, ApiErrorResponse(message = message))
                }
            }

            get("/payments/{paymentId}") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val paymentId = call.parameters["paymentId"]
                if (paymentId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "paymentId is required"))
                    return@get
                }

                val payment = store.payments.firstOrNull { it.id == paymentId }
                if (payment == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Payment not found"))
                    return@get
                }

                if (session.role == Role.CLIENT && session.clientId != payment.clientId) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
                    return@get
                }

                try {
                    val result = synchronized(stateLock) {
                        val refreshed = paymentService.refreshPaymentStatus(paymentId)
                        persistState()
                        refreshed
                    }
                    call.respond(
                        ApiPaymentStatusResponse(
                            paymentId = result.payment.id,
                            amountRub = result.payment.amountRub,
                            confirmationUrl = result.payment.confirmationUrl,
                            providerPaymentId = result.payment.providerPaymentId,
                            status = result.payment.status.name.lowercase(),
                            taxMode = result.payment.taxMode.name.lowercase(),
                            fiscalizationStatus = result.payment.fiscalizationStatus.name.lowercase(),
                            debtRub = result.debtRub
                        )
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = e.message ?: "Payment not found"))
                } catch (e: YooKassaException) {
                    call.application.environment.log.error("YooKassa payment status check failed", e)
                    call.respond(HttpStatusCode.BadGateway, ApiErrorResponse(message = "YooKassa payment status check failed"))
                }
            }

            get("/payments/{paymentId}/return") {
                val paymentId = call.parameters["paymentId"] ?: ""
                call.respondText(
                    """
                    <!doctype html>
                    <html lang="ru">
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1">
                      <title>Atom Go payment</title>
                      <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 32px; background: #eef4fb; color: #0b0f18; }
                        .card { max-width: 520px; margin: 12vh auto; background: white; border-radius: 24px; padding: 28px; box-shadow: 0 18px 50px rgba(0,0,0,.08); }
                        h1 { margin: 0 0 12px; }
                        p { line-height: 1.45; color: #5f6574; }
                      </style>
                    </head>
                    <body>
                      <main class="card">
                        <h1>Платёж отправлен на проверку</h1>
                        <p>Можно вернуться в приложение Atom Go. Статус платежа обновится автоматически после уведомления ЮKassa.</p>
                        <p>ID платежа: ${paymentId}</p>
                      </main>
                    </body>
                    </html>
                    """.trimIndent(),
                    ContentType.Text.Html
                )
            }

            post("/payments/yookassa/webhook") {
                val webhook = call.receive<JsonObject>()
                val event = webhook.string("event")
                val obj = webhook["object"]?.jsonObject
                val providerPaymentId = obj?.string("id")
                val providerStatus = obj?.string("status")?.let(::mapStatus)
                val amountRub = obj
                    ?.get("amount")
                    ?.jsonObject
                    ?.string("value")
                    ?.let { runCatching { decimalStringToRub(it) }.getOrNull() }
                val metadata = obj?.get("metadata")?.jsonObject
                val localPaymentId = metadata?.string("local_payment_id")

                if (event.isNullOrBlank() || providerPaymentId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "Invalid webhook payload"))
                    return@post
                }

                val result = try {
                    synchronized(stateLock) {
                        val webhookResult = paymentService.applyWebhook(
                            event = event,
                            providerPaymentId = providerPaymentId,
                            localPaymentId = localPaymentId,
                            providerStatusFromWebhook = providerStatus,
                            amountRubFromWebhook = amountRub
                        )
                        persistState()
                        webhookResult
                    }
                } catch (e: YooKassaException) {
                    call.application.environment.log.error("YooKassa webhook status check failed", e)
                    call.respond(HttpStatusCode.ServiceUnavailable, ApiErrorResponse(message = "YooKassa payment status check failed"))
                    return@post
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
