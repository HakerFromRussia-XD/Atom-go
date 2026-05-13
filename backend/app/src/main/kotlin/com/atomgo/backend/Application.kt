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
import com.atomgo.backend.domain.ClientRentalRecord
import com.atomgo.backend.domain.RentalRecord
import com.atomgo.backend.domain.RentalPipelineStatus
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
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("rental_start")
    val rentalStart: String,
    @SerialName("paid_until")
    val paidUntil: String,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("rental_is_active")
    val rentalIsActive: Boolean = false,
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
    val requiresReceiptEmail: Boolean,
    @SerialName("receipt_email")
    val receiptEmail: String? = null
)

@Serializable
private data class ApiAdminClientSummaryResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("rental_id")
    val rentalId: String? = null,
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
    @SerialName("rental_pipeline_status")
    val rentalPipelineStatus: String? = null,
    @SerialName("rental_is_active")
    val rentalIsActive: Boolean = false,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("profit_rub")
    val profitRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int,
    @SerialName("carried_debt_rub")
    val carriedDebtRub: Int = 0
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
    @SerialName("weekly_rate_rub")
    val weeklyRateRub: Int = 0,
    @SerialName("total_paid_rub")
    val totalPaidRub: Int = 0,
    @SerialName("debt_rub")
    val debtRub: Int = 0,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int = 0,
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
    val rentals: List<ApiAdminRentalHistoryItemResponse>,
    @SerialName("carried_debt_rub")
    val carriedDebtRub: Int = 0
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

/**
 * Запрос на admin-операцию с `ClientAccount.carriedDebtRub` (перенесённый долг,
 * см. docs/14_rental_lifecycle.md §7). Поддерживает:
 *  - kind = "writeoff" — админ списывает часть долга без оплаты (например, акция,
 *    компенсация). amount должен быть ≤ carriedDebt, иначе 400.
 *  - kind = "payment"  — админ принимает наличный/безналичный платёж вне YooKassa
 *    в счёт долга. До carriedDebt уходит в carriedDebt; излишек (amount > carriedDebt)
 *    автоматически уходит в активную клиентскую аренду клиента (если есть), иначе 400.
 */
@Serializable
private data class ApiAdminCarriedDebtOperationRequest(
    @SerialName("amount_rub")
    val amountRub: Int,
    val kind: String,
    val comment: String? = null
)

@Serializable
private data class ApiAdminCarriedDebtOperationResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("carried_debt_rub")
    val carriedDebtRub: Int,
    @SerialName("applied_to_carried_rub")
    val appliedToCarriedRub: Int,
    @SerialName("applied_to_active_rental_rub")
    val appliedToActiveRentalRub: Int,
    @SerialName("active_rental_id")
    val activeRentalId: String? = null
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
private data class ApiAdminStartClientRentalRequest(
    @SerialName("client_id")
    val clientId: String,
    val login: String,
    val password: String,
    @SerialName("period_start")
    val periodStart: String
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
private data class ApiAdminUpdateRentalPipelineStatusRequest(
    @SerialName("pipeline_status")
    val pipelineStatus: String
)

@Serializable
private data class ApiAdminUpdateRentalPipelineStatusResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("pipeline_status")
    val pipelineStatus: String
)

@Serializable
private data class ApiAdminFinishRentalResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("period_end")
    val periodEnd: String
)

@Serializable
private data class ApiAdminStartClientRentalResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("period_start")
    val periodStart: String,
    @SerialName("pipeline_status")
    val pipelineStatus: String
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
private data class ApiAdminRentalJournalEntryResponse(
    val type: String,
    @SerialName("amount_rub")
    val amountRub: Int,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
private data class ApiAdminRentalDetailsResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_full_name")
    val clientFullName: String,
    @SerialName("client_login")
    val clientLogin: String? = null,
    @SerialName("client_password")
    val clientPassword: String? = null,
    @SerialName("bike_id")
    val bikeId: String,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("weekly_rate_rub")
    val weeklyRateRub: Int,
    @SerialName("rental_start")
    val rentalStart: String,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("paid_until")
    val paidUntil: String,
    @SerialName("total_paid_rub")
    val totalPaidRub: Int,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int,
    @SerialName("rental_pipeline_status")
    val rentalPipelineStatus: String,
    @SerialName("rental_is_active")
    val rentalIsActive: Boolean,
    @SerialName("journal_entries")
    val journalEntries: List<ApiAdminRentalJournalEntryResponse>
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
    val clientRentalId: String,
    val rentalId: String,
    val clientId: String,
    val rentalStartDate: LocalDate,
    val rentalEndDate: LocalDate?,
    val weeklyRateRub: Int,
    val bikeModel: String,
    val bikePhotoUrl: String?,
    val taxMode: AdminTaxMode,
    val pipelineStatus: RentalPipelineStatus,
    val isActive: Boolean
)

private fun RentalRecord.isActiveAt(asOf: LocalDate): Boolean {
    return startDate <= asOf && (endDate == null || endDate.isAfter(asOf))
}

private fun ClientRentalRecord.isActiveAt(asOf: LocalDate): Boolean {
    return startDate <= asOf && (endDate == null || endDate.isAfter(asOf))
}

private fun clientRentalIdForLegacyRentalId(rentalId: String): String = "client-rental-$rentalId"

private fun lifecycleRentalForClientRental(
    clientRental: ClientRentalRecord,
    store: InMemoryStore
): RentalRecord? {
    return store.rentals.firstOrNull { it.id == clientRental.rentalId }
        ?: store.rentals.firstOrNull { it.bikeId == clientRental.bikeId && it.adminId == clientRental.adminId }
}

private fun activeClientRentalForLifecycle(
    rental: RentalRecord,
    store: InMemoryStore,
    asOf: LocalDate
): ClientRentalRecord? {
    return store.clientRentals
        .asSequence()
        .filter { it.rentalId == rental.id }
        .filter { it.adminId == rental.adminId }
        .filter { it.isActiveAt(asOf) }
        .sortedByDescending { it.startDate }
        .firstOrNull()
}

private fun resolveCurrentClientRental(
    clientId: String,
    store: InMemoryStore,
    asOf: LocalDate,
    adminId: String? = null,
    includeInactiveFallback: Boolean = true
): ClientRentalRecord? {
    val clientRentals = store.clientRentals
        .asSequence()
        .filter { it.clientId == clientId }
        .filter { adminId == null || it.adminId == adminId }
        .sortedByDescending { it.startDate }
        .toList()
    if (clientRentals.isEmpty()) return null

    val activeRental = clientRentals.firstOrNull { rental -> rental.isActiveAt(asOf) }
    if (activeRental != null) return activeRental
    return if (includeInactiveFallback) clientRentals.first() else null
}

private fun ensureClientRentalModel(store: InMemoryStore): Boolean {
    if (store.rentals.isEmpty()) return false

    var changed = false
    val legacyClientRentalIds = mutableMapOf<String, String>()

    if (store.clientRentals.isEmpty()) {
        store.rentals
            .filter { it.clientId.isNotBlank() }
            .forEach { rental ->
                val clientRentalId = clientRentalIdForLegacyRentalId(rental.id)
                legacyClientRentalIds[rental.id] = clientRentalId
                store.clientRentals += ClientRentalRecord(
                    id = clientRentalId,
                    rentalId = rental.id,
                    clientId = rental.clientId,
                    bikeId = rental.bikeId,
                    clientLogin = rental.clientLogin.orEmpty(),
                    clientPassword = rental.clientPassword.orEmpty(),
                    startDate = rental.startDate,
                    endDate = rental.endDate,
                    videoUrl = rental.videoUrl,
                    contractUrl = rental.contractUrl,
                    comment = rental.comment,
                    adminId = rental.adminId,
                    taxMode = rental.taxMode
                )
                changed = true
            }
    }

    if (legacyClientRentalIds.isNotEmpty()) {
        store.ledger.replaceAll { entry ->
            val mappedId = entry.rentalId?.let { legacyClientRentalIds[it] }
            if (mappedId == null) {
                entry
            } else {
                changed = true
                entry.copy(rentalId = mappedId)
            }
        }
        store.payments.replaceAll { payment ->
            val mappedId = payment.rentalId?.let { legacyClientRentalIds[it] }
            if (mappedId == null) {
                payment
            } else {
                changed = true
                payment.copy(rentalId = mappedId)
            }
        }
        store.sessions.entries.forEach { entry ->
            val mappedId = entry.value.rentalId?.let { legacyClientRentalIds[it] }
            if (mappedId != null) {
                entry.setValue(entry.value.copy(rentalId = mappedId))
                changed = true
            }
        }
    }

    val lifecycleByGroup = store.rentals
        .groupBy { "${it.adminId.orEmpty()}::${it.bikeId}" }
        .mapValues { (_, rentals) ->
            rentals
                .sortedWith(
                    compareByDescending<RentalRecord> {
                        it.pipelineStatus == RentalPipelineStatus.IN_STOCK || it.isActiveAt(LocalDate.now())
                    }.thenByDescending { it.startDate }
                        .thenByDescending { it.endDate ?: LocalDate.MAX }
                )
                .first()
        }

    store.clientRentals.replaceAll { clientRental ->
        val lifecycle = lifecycleByGroup["${clientRental.adminId.orEmpty()}::${clientRental.bikeId}"]
        if (lifecycle != null && clientRental.rentalId != lifecycle.id) {
            changed = true
            clientRental.copy(rentalId = lifecycle.id)
        } else {
            clientRental
        }
    }

    val lifecycleIds = lifecycleByGroup.values.map { it.id }.toSet()
    if (store.rentals.removeAll { it.id !in lifecycleIds }) {
        changed = true
    }

    // Инвариант: у каждой ClientRentalRecord должны быть непустые
    // clientLogin и clientPassword (docs/14_rental_lifecycle.md §4).
    // Если запись пришла из legacy-миграции с пустыми credentials или из
    // seed-данных без явных логина/пароля — пробуем заполнить:
    //   1) из исходной RentalRecord.clientLogin/Password (старый источник);
    //   2) из AppUser клиента (последний известный логин/пароль).
    // Этот шаг идемпотентен: запись с уже заполненными credentials не меняется.
    store.clientRentals.replaceAll { clientRental ->
        val needsLogin = clientRental.clientLogin.isBlank()
        val needsPassword = clientRental.clientPassword.isBlank()
        if (!needsLogin && !needsPassword) return@replaceAll clientRental

        val legacyRental = store.rentals.firstOrNull { it.id == clientRental.rentalId }
        val fallbackUser = store.users.firstOrNull {
            it.role == Role.CLIENT && it.clientId == clientRental.clientId
        }
        val backfilledLogin = clientRental.clientLogin.ifBlank {
            legacyRental?.clientLogin?.takeIf { it.isNotBlank() }
                ?: fallbackUser?.login?.takeIf { it.isNotBlank() }
                ?: ""
        }
        val backfilledPassword = clientRental.clientPassword.ifBlank {
            legacyRental?.clientPassword?.takeIf { it.isNotBlank() }
                ?: fallbackUser?.password?.takeIf { it.isNotBlank() }
                ?: ""
        }
        if (backfilledLogin == clientRental.clientLogin && backfilledPassword == clientRental.clientPassword) {
            clientRental
        } else {
            changed = true
            clientRental.copy(clientLogin = backfilledLogin, clientPassword = backfilledPassword)
        }
    }

    store.rentals.replaceAll { rental ->
        val activeClientRental = activeClientRentalForLifecycle(rental, store, LocalDate.now())
        if (activeClientRental == null) {
            if (
                rental.clientId.isNotBlank() ||
                rental.clientLogin != null ||
                rental.clientPassword != null ||
                rental.pipelineStatus != RentalPipelineStatus.IN_STOCK
            ) {
                changed = true
                rental.copy(
                    clientId = "",
                    clientLogin = null,
                    clientPassword = null,
                    endDate = rental.endDate ?: LocalDate.now(),
                    pipelineStatus = RentalPipelineStatus.IN_STOCK
                )
            } else {
                rental
            }
        } else if (
            rental.clientId != activeClientRental.clientId ||
            rental.clientLogin != null ||
            rental.clientPassword != null ||
            rental.startDate != activeClientRental.startDate ||
            rental.endDate != null
        ) {
            changed = true
            rental.copy(
                clientId = activeClientRental.clientId,
                clientLogin = null,
                clientPassword = null,
                startDate = activeClientRental.startDate,
                endDate = null,
                pipelineStatus = if (rental.pipelineStatus == RentalPipelineStatus.IN_STOCK) {
                    RentalPipelineStatus.LONG_TERM
                } else {
                    rental.pipelineStatus
                }
            )
        } else {
            rental
        }
    }

    return changed
}

private fun resolveClientBillingSnapshot(
    clientId: String,
    store: InMemoryStore,
    asOf: LocalDate,
    adminId: String? = null,
    targetRentalId: String? = null,
    includeInactiveFallback: Boolean = true
): ClientBillingSnapshot? {
    val clientRental = if (targetRentalId != null) {
        store.clientRentals.firstOrNull { rental ->
            rental.id == targetRentalId &&
                rental.clientId == clientId &&
                (adminId == null || rental.adminId == adminId)
        }
    } else {
        resolveCurrentClientRental(clientId = clientId, store = store, asOf = asOf, adminId = adminId)
            ?.takeIf { includeInactiveFallback || it.isActiveAt(asOf) }
    } ?: return null
    val bike = store.bikes.firstOrNull { it.id == clientRental.bikeId } ?: return null
    val lifecycleRental = lifecycleRentalForClientRental(clientRental, store)
    return ClientBillingSnapshot(
        clientRentalId = clientRental.id,
        rentalId = clientRental.rentalId,
        clientId = clientRental.clientId,
        rentalStartDate = clientRental.startDate,
        rentalEndDate = clientRental.endDate,
        weeklyRateRub = bike.weeklyRateRub,
        bikeModel = bike.model,
        bikePhotoUrl = bike.photoUrl,
        taxMode = clientRental.taxMode,
        pipelineStatus = lifecycleRental?.pipelineStatus ?: RentalPipelineStatus.LONG_TERM,
        isActive = clientRental.isActiveAt(asOf)
    )
}

private fun extractClientReceiptEmail(client: ClientAccount): String? {
    return client.phones
        .asSequence()
        .mapNotNull { normalizeReceiptEmail(it.number) }
        .firstOrNull()
}

private fun clientHasReceiptEmail(client: ClientAccount): Boolean = extractClientReceiptEmail(client) != null

private fun normalizeReceiptEmail(rawEmail: String?): String? {
    val email = rawEmail?.trim()?.lowercase().orEmpty()
    if (email.isBlank() || email.length > 254 || !email.contains("@")) return null
    val parts = email.split("@")
    if (parts.size != 2 || parts.any { it.isBlank() }) return null
    if (!parts[1].contains(".")) return null
    return email
}

private fun ledgerSignedAmountForUi(entry: LedgerEntry): Int = when (entry.type) {
    LedgerType.PAYMENT -> if (entry.direction == -1) entry.amountRub else -entry.amountRub
    LedgerType.CHARGE -> if (entry.direction == 1) -entry.amountRub else entry.amountRub
    LedgerType.ADJUSTMENT -> if (entry.direction == -1) -entry.amountRub else entry.amountRub
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
    return client.adminId == adminId || store.clientRentals.any { it.clientId == clientId && it.adminId == adminId }
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

private data class RentalCredentials(
    val login: String? = null,
    val password: String? = null
)

private fun normalizeCredential(value: String?): String? {
    val normalized = value?.trim().orEmpty()
    return normalized.ifBlank { null }
}

private fun resolveRentalCredentials(store: InMemoryStore, rental: RentalRecord): RentalCredentials {
    val activeClientRental = activeClientRentalForLifecycle(rental, store, LocalDate.now())
    val storedLogin = normalizeCredential(activeClientRental?.clientLogin ?: rental.clientLogin)
    val storedPassword = normalizeCredential(activeClientRental?.clientPassword ?: rental.clientPassword)
    if (storedLogin != null && storedPassword != null) {
        return RentalCredentials(login = storedLogin, password = storedPassword)
    }

    val loginUser = store.users.firstOrNull {
        it.role == Role.CLIENT && it.clientId == (activeClientRental?.clientId ?: rental.clientId)
    }
    return RentalCredentials(
        login = storedLogin ?: normalizeCredential(loginUser?.login),
        password = storedPassword ?: normalizeCredential(loginUser?.password)
    )
}

private fun resolveClientRentalCredentials(clientRental: ClientRentalRecord): RentalCredentials {
    return RentalCredentials(
        login = normalizeCredential(clientRental.clientLogin),
        password = normalizeCredential(clientRental.clientPassword)
    )
}

private sealed class RentalCreationOutcome {
    data class Success(val rental: ApiAdminRentalHistoryItemResponse) : RentalCreationOutcome()
    data class Failure(val status: HttpStatusCode, val message: String) : RentalCreationOutcome()
}

private sealed class StartClientRentalOutcome {
    data class Success(val response: ApiAdminStartClientRentalResponse) : StartClientRentalOutcome()
    data class Failure(val status: HttpStatusCode, val message: String) : StartClientRentalOutcome()
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
    if (store.rentals.any { it.bikeId == bike.id && it.adminId == adminId }) {
        return RentalCreationOutcome.Failure(HttpStatusCode.Conflict, "bike already has rental")
    }

    return synchronized(stateLock) {
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
            clientLogin = null,
            clientPassword = null,
            startDate = periodStart,
            endDate = periodEnd,
            videoUrl = null,
            contractUrl = null,
            comment = null,
            adminId = adminId,
            taxMode = taxMode
        )
        val clientRental = ClientRentalRecord(
            id = "client-rental-${UUID.randomUUID().toString().take(8)}",
            rentalId = rental.id,
            clientId = client.id,
            bikeId = bike.id,
            clientLogin = login,
            clientPassword = password,
            startDate = periodStart,
            endDate = periodEnd,
            videoUrl = request.videoUrl?.trim()?.ifBlank { null },
            contractUrl = request.contractUrl?.trim()?.ifBlank { null },
            comment = request.comment?.trim()?.ifBlank { null },
            adminId = adminId,
            taxMode = taxMode
        )
        store.rentals += rental
        store.clientRentals += clientRental
        persistState()
        RentalCreationOutcome.Success(
            ApiAdminRentalHistoryItemResponse(
                rentalId = rental.id,
                bikeId = bike.id,
                bikeAvatarUrl = bike.photoUrl ?: "",
                periodStart = clientRental.startDate.toString(),
                periodEnd = clientRental.endDate?.toString(),
                bikeModel = bike.model,
                videoUrl = clientRental.videoUrl,
                contractUrl = clientRental.contractUrl,
                comment = clientRental.comment,
                adminId = rental.adminId,
                taxMode = rental.taxMode.name.lowercase()
            )
        )
    }
}

private fun startClientRentalInExistingRental(
    store: InMemoryStore,
    stateLock: Any,
    persistState: () -> Unit,
    adminId: String,
    rentalId: String,
    request: ApiAdminStartClientRentalRequest
): StartClientRentalOutcome {
    val clientId = request.clientId.trim()
    val login = request.login.trim()
    val password = request.password.trim()
    val periodStartRaw = request.periodStart.trim()

    if (clientId.isBlank()) {
        return StartClientRentalOutcome.Failure(HttpStatusCode.BadRequest, "client_id is required")
    }
    if (login.isBlank() || password.isBlank()) {
        return StartClientRentalOutcome.Failure(HttpStatusCode.BadRequest, "login and password are required")
    }
    if (periodStartRaw.isBlank()) {
        return StartClientRentalOutcome.Failure(HttpStatusCode.BadRequest, "period_start is required")
    }

    val periodStart = try {
        LocalDate.parse(periodStartRaw)
    } catch (_: Throwable) {
        return StartClientRentalOutcome.Failure(HttpStatusCode.BadRequest, "period_start must be YYYY-MM-DD")
    }

    return synchronized(stateLock) {
        val rentalIndex = store.rentals.indexOfFirst { it.id == rentalId }
        if (rentalIndex < 0) {
            return@synchronized StartClientRentalOutcome.Failure(HttpStatusCode.NotFound, "rental not found")
        }

        val currentRental = store.rentals[rentalIndex]
        if (currentRental.adminId != adminId) {
            return@synchronized StartClientRentalOutcome.Failure(HttpStatusCode.NotFound, "rental not found")
        }

        val today = LocalDate.now()
        if (activeClientRentalForLifecycle(currentRental, store, today) != null) {
            return@synchronized StartClientRentalOutcome.Failure(HttpStatusCode.Conflict, "rental is already active")
        }

        val client = store.clients.firstOrNull { it.id == clientId }
            ?: return@synchronized StartClientRentalOutcome.Failure(HttpStatusCode.NotFound, "client not found")
        if (!adminOwnsClient(store, adminId, client.id)) {
            return@synchronized StartClientRentalOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
        }

        val clientHasActiveRental = store.clientRentals.any {
            it.rentalId != currentRental.id &&
                it.clientId == client.id &&
                it.adminId == adminId &&
                it.isActiveAt(today)
        }
        if (clientHasActiveRental) {
            return@synchronized StartClientRentalOutcome.Failure(HttpStatusCode.Conflict, "client already has active rental")
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

        store.sessions.entries.removeAll { it.value.clientId == client.id }

        val restartedRental = currentRental.copy(
            clientId = client.id,
            clientLogin = null,
            clientPassword = null,
            startDate = periodStart,
            endDate = null,
            pipelineStatus = RentalPipelineStatus.LONG_TERM
        )
        val clientRental = ClientRentalRecord(
            id = "client-rental-${UUID.randomUUID().toString().take(8)}",
            rentalId = currentRental.id,
            clientId = client.id,
            bikeId = currentRental.bikeId,
            clientLogin = login,
            clientPassword = password,
            startDate = periodStart,
            endDate = null,
            videoUrl = null,
            contractUrl = null,
            comment = null,
            adminId = adminId,
            taxMode = currentRental.taxMode
        )
        store.clientRentals += clientRental
        store.rentals[rentalIndex] = restartedRental
        persistState()

        StartClientRentalOutcome.Success(
            ApiAdminStartClientRentalResponse(
                rentalId = restartedRental.id,
                clientId = restartedRental.clientId,
                periodStart = restartedRental.startDate.toString(),
                pipelineStatus = RentalPipelineStatus.toApi(restartedRental.pipelineStatus)
            )
        )
    }
}

private fun transitionRentalToInStock(
    store: InMemoryStore,
    rentalIndex: Int,
    today: LocalDate
): RentalRecord {
    val current = store.rentals[rentalIndex]
    val activeClientRental = activeClientRentalForLifecycle(current, store, today)
    if (activeClientRental != null) {
        val clientRentalIndex = store.clientRentals.indexOfFirst { it.id == activeClientRental.id }
        if (clientRentalIndex >= 0) {
            store.clientRentals[clientRentalIndex] = activeClientRental.copy(endDate = today)
        }
    }
    val next = current.copy(
        clientId = "",
        clientLogin = null,
        clientPassword = null,
        endDate = today,
        pipelineStatus = RentalPipelineStatus.IN_STOCK
    )
    store.rentals[rentalIndex] = next
    if (activeClientRental?.clientId?.isNotBlank() == true) {
        // "Велосипед у меня": клиент больше не привязан к активной аренде.
        // Очищаем только активные клиентские сессии.
        // Логин/пароль должны оставаться валидными для просмотра завершенной клиентской аренды.
        store.sessions.entries.removeAll { it.value.clientId == activeClientRental.clientId }
    }
    return next
}

/**
 * Удаление lifecycle-аренды (вывод велосипеда из эксплуатации).
 *
 * Согласно docs/14_rental_lifecycle.md §7 и docs/02_money_and_debt_rules.md §7:
 * 1. Если есть активная клиентская аренда — закрываем её сегодняшней датой.
 * 2. Считаем финальный долг по дням (LedgerCalculator.finalDebtOnClosure).
 * 3. Если долг > 0 — переносим его в ClientAccount.carriedDebtRub
 *    (накопительно), чтобы он не потерялся после удаления карточки.
 * 4. Удаляем активные сессии бывшего клиента (логин закрытой client_rental
 *    остаётся валидным для просмотра/оплаты долга).
 * 5. Удаляем lifecycle-карточку из store.rentals.
 *
 * История закрытой клиентской аренды (ledger, payments, credentials,
 * comment, ссылки) остаётся в store.clientRentals и продолжает быть
 * доступной через GET /admin/rentals/{clientRentalId} и через старый
 * логин клиента.
 */
private fun deleteLifecycleRental(
    store: InMemoryStore,
    rentalIndex: Int,
    today: LocalDate
) {
    val current = store.rentals[rentalIndex]
    activeClientRentalForLifecycle(current, store, today)?.let { activeClientRental ->
        val clientRentalIndex = store.clientRentals.indexOfFirst { it.id == activeClientRental.id }
        val closedClientRental = activeClientRental.copy(endDate = today)
        if (clientRentalIndex >= 0) {
            store.clientRentals[clientRentalIndex] = closedClientRental
        }

        // Финальный долг считаем по тарифу велосипеда lifecycle-аренды.
        val bike = store.bikes.firstOrNull { it.id == current.bikeId }
        val weeklyRate = bike?.weeklyRateRub ?: 0
        val finalDebt = if (weeklyRate > 0) {
            LedgerCalculator.finalDebtOnClosure(
                clientId = closedClientRental.clientId,
                rentalStartDate = closedClientRental.startDate,
                rentalEndDate = today,
                weeklyRateRub = weeklyRate,
                entries = store.ledger,
                rentalId = closedClientRental.id
            )
        } else {
            0
        }

        if (finalDebt > 0) {
            val clientIdx = store.clients.indexOfFirst { it.id == closedClientRental.clientId }
            if (clientIdx >= 0) {
                val existing = store.clients[clientIdx]
                store.clients[clientIdx] = existing.copy(
                    carriedDebtRub = existing.carriedDebtRub + finalDebt
                )
            }
        }

        store.sessions.entries.removeAll { it.value.clientId == activeClientRental.clientId }
    }
    store.rentals.removeAt(rentalIndex)
}

private fun findLifecycleRentalIndexForDeletion(
    store: InMemoryStore,
    adminId: String,
    rentalId: String
): Int {
    val lifecycleIndex = store.rentals.indexOfFirst { it.id == rentalId && it.adminId == adminId }
    if (lifecycleIndex >= 0) return lifecycleIndex

    val clientRental = store.clientRentals.firstOrNull { it.id == rentalId && it.adminId == adminId }
        ?: return -1
    return store.rentals.indexOfFirst { it.id == clientRental.rentalId && it.adminId == adminId }
}

private sealed class CarriedDebtOutcome {
    data class Success(val response: ApiAdminCarriedDebtOperationResponse) : CarriedDebtOutcome()
    data class Failure(val status: HttpStatusCode, val message: String) : CarriedDebtOutcome()
}

/**
 * Применяет операцию над перенесённым долгом клиента
 * (см. docs/14_rental_lifecycle.md §7, docs/02_money_and_debt_rules.md §7).
 *
 * Вызывающий должен заранее: проверить admin-сессию, валидировать
 * `amountRub > 0` и `kind ∈ {writeoff, payment}`, обернуть в synchronized(stateLock).
 * Persist делает вызывающий, только если результат Success.
 *
 * Поведение:
 * - writeoff: amount ≤ carriedDebt → списать; иначе 400.
 * - payment:  до carriedDebt уходит в carriedDebt (ledger PAYMENT без rentalId).
 *             Излишек (amount − carriedDebt) уходит в активную client_rental клиента
 *             (PAYMENT с rentalId этой client_rental). Если активной нет — 400.
 * Каждая мутация сопровождается аудитной записью в ledger.
 */
private fun applyCarriedDebtOperation(
    store: InMemoryStore,
    adminId: String,
    clientId: String,
    amountRub: Int,
    kind: String,
    comment: String?,
    today: LocalDate
): CarriedDebtOutcome {
    val clientIdx = store.clients.indexOfFirst { it.id == clientId }
    if (clientIdx < 0) {
        return CarriedDebtOutcome.Failure(HttpStatusCode.NotFound, "Client not found")
    }
    val client = store.clients[clientIdx]
    if (!adminOwnsClient(store, adminId, client.id)) {
        return CarriedDebtOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
    }

    val carried = client.carriedDebtRub
    val nowInstant = java.time.Instant.now()
    val noteSuffix = comment?.trim()?.ifBlank { null }?.let { " ($it)" } ?: ""

    return when (kind) {
        "writeoff" -> {
            if (amountRub > carried) {
                CarriedDebtOutcome.Failure(
                    HttpStatusCode.BadRequest,
                    "amount_rub exceeds carried_debt_rub"
                )
            } else {
                store.clients[clientIdx] = client.copy(carriedDebtRub = carried - amountRub)
                store.ledger += LedgerEntry(
                    id = "carried-writeoff-${java.util.UUID.randomUUID().toString().take(8)}",
                    clientId = client.id,
                    type = LedgerType.ADJUSTMENT,
                    direction = -1,
                    amountRub = amountRub,
                    createdAt = nowInstant,
                    note = "Списание перенесённого долга$noteSuffix",
                    rentalId = null
                )
                CarriedDebtOutcome.Success(
                    ApiAdminCarriedDebtOperationResponse(
                        clientId = client.id,
                        carriedDebtRub = carried - amountRub,
                        appliedToCarriedRub = amountRub,
                        appliedToActiveRentalRub = 0,
                        activeRentalId = null
                    )
                )
            }
        }

        "payment" -> {
            val toCarried = minOf(amountRub, carried)
            val excess = amountRub - toCarried

            val activeClientRental = if (excess > 0) {
                store.clientRentals
                    .filter { it.clientId == client.id && it.adminId == adminId }
                    .firstOrNull { it.isActiveAt(today) }
            } else {
                null
            }

            if (excess > 0 && activeClientRental == null) {
                return CarriedDebtOutcome.Failure(
                    HttpStatusCode.BadRequest,
                    "amount_rub exceeds carried_debt_rub and no active rental to apply excess"
                )
            }

            if (toCarried > 0) {
                store.clients[clientIdx] = client.copy(carriedDebtRub = carried - toCarried)
                store.ledger += LedgerEntry(
                    id = "carried-payment-${java.util.UUID.randomUUID().toString().take(8)}",
                    clientId = client.id,
                    type = LedgerType.PAYMENT,
                    direction = -1,
                    amountRub = toCarried,
                    createdAt = nowInstant,
                    note = "Платёж по перенесённому долгу$noteSuffix",
                    rentalId = null
                )
            }
            if (excess > 0 && activeClientRental != null) {
                store.ledger += LedgerEntry(
                    id = "carried-excess-${java.util.UUID.randomUUID().toString().take(8)}",
                    clientId = client.id,
                    type = LedgerType.PAYMENT,
                    direction = -1,
                    amountRub = excess,
                    createdAt = nowInstant,
                    note = "Излишек платежа в активную аренду$noteSuffix",
                    rentalId = activeClientRental.id
                )
            }

            CarriedDebtOutcome.Success(
                ApiAdminCarriedDebtOperationResponse(
                    clientId = client.id,
                    carriedDebtRub = carried - toCarried,
                    appliedToCarriedRub = toCarried,
                    appliedToActiveRentalRub = excess,
                    activeRentalId = activeClientRental?.id
                )
            )
        }

        else -> CarriedDebtOutcome.Failure(
            HttpStatusCode.BadRequest,
            "kind must be writeoff or payment"
        )
    }
}

private fun buildAdminClientSummary(
    client: ClientAccount,
    store: InMemoryStore,
    now: LocalDate,
    adminId: String? = null
): ApiAdminClientSummaryResponse {
    val snapshot = resolveClientBillingSnapshot(
        client.id,
        store,
        now,
        adminId,
        includeInactiveFallback = false
    )
    val projection = if (snapshot?.isActive == true) {
        LedgerCalculator.billingProjection(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger,
            asOf = now,
            rentalId = snapshot.clientRentalId
        )
    } else {
        null
    }
    val debt = projection?.debtRub ?: 0
    val paid = LedgerCalculator.totalPaidRub(store.ledger, client.id, snapshot?.clientRentalId)
    val profit = if (debt == 0) paid else 0
    val paidUntil = projection?.paidUntilDate
    val statusText = projection?.statusText ?: "Нет активной аренды"
    val snapshotClientRental = snapshot?.clientRentalId?.let { snapshotRentalId ->
        store.clientRentals.firstOrNull { it.id == snapshotRentalId }
    }
    val credentials = snapshotClientRental?.let { resolveClientRentalCredentials(it) } ?: RentalCredentials()

    return ApiAdminClientSummaryResponse(
        clientId = client.id,
        rentalId = snapshot?.rentalId,
        clientLogin = credentials.login,
        fullName = client.fullName,
        bikeModel = snapshot?.bikeModel ?: "-",
        bikeAvatarUrl = snapshot?.bikePhotoUrl ?: "",
        statusText = statusText,
        paidUntil = paidUntil?.toString(),
        rentalPipelineStatus = snapshot?.pipelineStatus?.let(RentalPipelineStatus::toApi),
        rentalIsActive = snapshot?.isActive == true,
        debtRub = debt,
        profitRub = profit,
        totalAdjustmentRub = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.clientRentalId),
        carriedDebtRub = client.carriedDebtRub
    )
}

private fun buildAdminRentSummaryFromRental(
    rental: RentalRecord,
    store: InMemoryStore,
    now: LocalDate
): ApiAdminClientSummaryResponse {
    val bike = store.bikes.firstOrNull { it.id == rental.bikeId }
    val activeClientRental = activeClientRentalForLifecycle(rental, store, now)
    val client = activeClientRental?.let { store.clients.firstOrNull { client -> client.id == it.clientId } }

    if (activeClientRental != null && client != null) {
        return buildAdminClientSummary(
            client = client,
            store = store,
            now = now,
            adminId = rental.adminId
        ).copy(rentalId = rental.id)
    }

    return ApiAdminClientSummaryResponse(
        clientId = "",
            rentalId = rental.id,
        clientLogin = null,
        fullName = "",
        bikeModel = bike?.model ?: "",
        bikeAvatarUrl = bike?.photoUrl ?: "",
        statusText = "У меня",
        paidUntil = null,
        rentalPipelineStatus = rental.pipelineStatus.let(RentalPipelineStatus::toApi),
            rentalIsActive = false,
        debtRub = 0,
        profitRub = 0,
        totalAdjustmentRub = 0
    )
}

private fun buildAdminClientDetails(
    client: ClientAccount,
    store: InMemoryStore,
    now: LocalDate,
    adminId: String? = null
): ApiAdminClientDetailsResponse {
    val snapshot = resolveClientBillingSnapshot(
        client.id,
        store,
        now,
        adminId,
        includeInactiveFallback = false
    )
    val projection = if (snapshot?.isActive == true) {
        LedgerCalculator.billingProjection(
            clientId = client.id,
            rentalStartDate = snapshot.rentalStartDate,
            weeklyRateRub = snapshot.weeklyRateRub,
            entries = store.ledger,
            asOf = now,
            rentalId = snapshot.clientRentalId
        )
    } else {
        null
    }
    val rentals = store.clientRentals
        .asSequence()
        .filter { it.clientId == client.id }
        .filter { adminId == null || it.adminId == adminId }
        .sortedByDescending { it.startDate }
        .map {
            val bike = store.bikes.firstOrNull { bike -> bike.id == it.bikeId }
            val weeklyRateRub = bike?.weeklyRateRub ?: 0
            val rentalAsOf = it.endDate ?: now
            val rentalPaidRub = LedgerCalculator.totalPaidRub(store.ledger, client.id, it.id)
            val rentalAdjustmentRub = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, it.id)
            val rentalDebtRub = if (weeklyRateRub > 0) {
                LedgerCalculator.debtRub(
                    clientId = client.id,
                    rentalStartDate = it.startDate,
                    weeklyRateRub = weeklyRateRub,
                    entries = store.ledger,
                    asOf = rentalAsOf,
                    rentalId = it.id
                )
            } else {
                0
            }
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
                weeklyRateRub = weeklyRateRub,
                totalPaidRub = rentalPaidRub,
                debtRub = rentalDebtRub,
                totalAdjustmentRub = rentalAdjustmentRub,
                adminId = it.adminId,
                taxMode = it.taxMode.name.lowercase()
            )
        }
        .toList()
    val totalPaid = rentals.sumOf { it.totalPaidRub }
    val debt = rentals.sumOf { it.debtRub }
    val totalAdjustment = rentals.sumOf { it.totalAdjustmentRub }
    val paidUntil = projection?.paidUntilDate?.toString() ?: ""

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
        rentals = rentals,
        carriedDebtRub = client.carriedDebtRub
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
        encodeDefaults = true
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
    val saveState: () -> Unit = {
        stateStore?.save(store)
    }
    val normalizeStoreState: () -> Boolean = {
        val ownershipChanged = ensureDefaultAdminsAndOwnership(store)
        val rentalsChanged = ensureClientRentalModel(store)
        ownershipChanged || rentalsChanged
    }
    val persistState: () -> Unit = {
        normalizeStoreState()
        saveState()
    }
    if (normalizeStoreState()) {
        saveState()
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
                val snapshot = resolveClientBillingSnapshot(
                    clientId = client.id,
                    store = store,
                    asOf = now,
                    targetRentalId = session.rentalId
                )
                val projection = if (snapshot != null) {
                    val chargeAsOf = if (snapshot.isActive) {
                        now
                    } else {
                        snapshot.rentalEndDate ?: now
                    }
                    LedgerCalculator.billingProjection(
                        clientId = client.id,
                        rentalStartDate = snapshot.rentalStartDate,
                        weeklyRateRub = snapshot.weeklyRateRub,
                        entries = store.ledger,
                        asOf = chargeAsOf,
                        rentalId = snapshot.clientRentalId
                    )
                } else {
                    null
                }
                val debt = projection?.debtRub ?: 0
                val paidUntil = projection?.paidUntilDate?.toString() ?: ""
                val balanceRub = projection?.balanceRub ?: 0
                val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.clientRentalId)
                val weeklyRate = snapshot?.weeklyRateRub ?: 0

                call.respond(
                    ApiClientDashboardResponse(
                        clientId = client.id,
                        bikeModel = snapshot?.bikeModel ?: "",
                        bikeAvatarUrl = snapshot?.bikePhotoUrl ?: "",
                        rentalStart = snapshot?.rentalStartDate?.toString() ?: "",
                        paidUntil = paidUntil,
                        completedAt = snapshot?.rentalEndDate?.toString(),
                        rentalIsActive = snapshot?.isActive == true,
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
                            !clientHasReceiptEmail(client),
                        receiptEmail = extractClientReceiptEmail(client)
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
                val response = synchronized(stateLock) {
                    if (normalizeStoreState()) {
                        saveState()
                    }
                    store.rentals
                        .asSequence()
                        .filter { rental -> rental.adminId == session.userId }
                        .sortedByDescending { rental -> rental.startDate }
                        .map { rental -> buildAdminRentSummaryFromRental(rental, store, now) }
                        .toList()
                }
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

            get("/admin/rentals/{rentalId}") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val rentalId = call.parameters["rentalId"]
                if (rentalId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "rentalId is required"))
                    return@get
                }

                val now = LocalDate.now()
                val response = synchronized(stateLock) {
                    if (normalizeStoreState()) {
                        saveState()
                    }
                    val lifecycleRental = store.rentals.firstOrNull { it.id == rentalId && it.adminId == session.userId }
                    val targetClientRental = if (lifecycleRental != null) {
                        activeClientRentalForLifecycle(lifecycleRental, store, now)
                    } else {
                        store.clientRentals.firstOrNull { it.id == rentalId && it.adminId == session.userId }
                    }
                    val rental = lifecycleRental ?: targetClientRental?.let { lifecycleRentalForClientRental(it, store) }
                    val bikeId = targetClientRental?.bikeId ?: rental?.bikeId ?: return@synchronized null
                    val bike = store.bikes.firstOrNull { it.id == bikeId } ?: return@synchronized null
                    val client = targetClientRental?.let { store.clients.firstOrNull { client -> client.id == it.clientId } }
                    val rentalIsActive = targetClientRental?.isActiveAt(now) == true
                    val projection = if (targetClientRental != null) {
                        LedgerCalculator.billingProjection(
                            clientId = targetClientRental.clientId,
                            rentalStartDate = targetClientRental.startDate,
                            weeklyRateRub = bike.weeklyRateRub,
                            entries = store.ledger,
                            asOf = if (rentalIsActive) now else (targetClientRental.endDate ?: now),
                            rentalId = targetClientRental.id
                        )
                    } else {
                        null
                    }
                    val credentials = targetClientRental?.let(::resolveClientRentalCredentials) ?: RentalCredentials()
                    val journal = if (targetClientRental == null || lifecycleRental?.pipelineStatus == RentalPipelineStatus.IN_STOCK) {
                        emptyList()
                    } else {
                        store.ledger
                            .asSequence()
                            .filter { entry ->
                                entry.rentalId == targetClientRental.id ||
                                    (entry.clientId == targetClientRental.clientId && entry.rentalId == null)
                            }
                            .sortedByDescending { it.createdAt }
                            .map { entry ->
                                ApiAdminRentalJournalEntryResponse(
                                    type = entry.type.name.lowercase(),
                                    amountRub = ledgerSignedAmountForUi(entry),
                                    createdAt = entry.createdAt.toString()
                                )
                            }
                            .toList()
                    }

                    ApiAdminRentalDetailsResponse(
                        rentalId = lifecycleRental?.id ?: targetClientRental?.id ?: rental?.id.orEmpty(),
                        clientId = targetClientRental?.clientId ?: "",
                        clientFullName = client?.fullName ?: "",
                        clientLogin = credentials.login,
                        clientPassword = credentials.password,
                        bikeId = bike.id,
                        bikeModel = bike.model,
                        bikeAvatarUrl = bike.photoUrl ?: "",
                        weeklyRateRub = bike.weeklyRateRub,
                        rentalStart = targetClientRental?.startDate?.toString() ?: rental?.startDate?.toString().orEmpty(),
                        completedAt = targetClientRental?.endDate?.toString(),
                        paidUntil = projection?.paidUntilDate?.toString() ?: "",
                        totalPaidRub = if (targetClientRental != null) {
                            LedgerCalculator.totalPaidRub(store.ledger, targetClientRental.clientId, targetClientRental.id)
                        } else {
                            0
                        },
                        debtRub = projection?.debtRub ?: 0,
                        totalAdjustmentRub = if (targetClientRental != null) {
                            LedgerCalculator.totalAdjustmentRub(store.ledger, targetClientRental.clientId, targetClientRental.id)
                        } else {
                            0
                        },
                        rentalPipelineStatus = RentalPipelineStatus.toApi(rental?.pipelineStatus ?: RentalPipelineStatus.LONG_TERM),
                        rentalIsActive = rentalIsActive,
                        journalEntries = journal
                    )
                }

                if (response == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, response)
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
                        if (store.clientRentals.any { it.clientId == clientId && it.adminId == session.userId }) {
                            return@synchronized HttpStatusCode.Conflict to "client is used by rentals"
                        }
                        store.clients.removeAt(clientIndex)
                        store.clientRentals.removeAll { it.clientId == clientId }
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
                        note = request.comment?.trim()?.ifBlank { null },
                        rentalId = resolveClientBillingSnapshot(client.id, store, LocalDate.now(), session.userId, includeInactiveFallback = false)?.clientRentalId
                    )

                    persistState()
                    val now = LocalDate.now()
                    val snapshot = resolveClientBillingSnapshot(client.id, store, now, session.userId)
                    val debt = if (snapshot?.isActive == true) {
                        LedgerCalculator.debtRub(
                            clientId = client.id,
                            rentalStartDate = snapshot.rentalStartDate,
                            weeklyRateRub = snapshot.weeklyRateRub,
                            entries = store.ledger,
                            asOf = now,
                            rentalId = snapshot.clientRentalId
                        )
                    } else {
                        0
                    }
                    val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.clientRentalId)
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

            /**
             * Admin-операции с перенесённым долгом клиента
             * (docs/14_rental_lifecycle.md §7).
             */
            post("/admin/clients/{clientId}/carried-debt") {
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

                val request = call.receive<ApiAdminCarriedDebtOperationRequest>()
                if (request.amountRub <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "amount_rub must be positive"))
                    return@post
                }
                val normalizedKind = request.kind.trim().lowercase()
                if (normalizedKind != "writeoff" && normalizedKind != "payment") {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "kind must be writeoff or payment"))
                    return@post
                }

                val result = synchronized(stateLock) {
                    if (normalizeStoreState()) {
                        saveState()
                    }
                    applyCarriedDebtOperation(
                        store = store,
                        adminId = session.userId,
                        clientId = clientId,
                        amountRub = request.amountRub,
                        kind = normalizedKind,
                        comment = request.comment,
                        today = LocalDate.now()
                    ).also { outcome ->
                        if (outcome is CarriedDebtOutcome.Success) {
                            persistState()
                        }
                    }
                }

                when (result) {
                    is CarriedDebtOutcome.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is CarriedDebtOutcome.Failure -> call.respond(
                        result.status,
                        ApiErrorResponse(message = result.message)
                    )
                }
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

                val lifecycleRental = store.rentals.firstOrNull { it.id == rentalId }
                val clientRental = store.clientRentals.firstOrNull { it.id == rentalId }
                val ownerAdminId = lifecycleRental?.adminId ?: clientRental?.adminId
                if (lifecycleRental == null && clientRental == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }
                if (ownerAdminId != session.userId) {
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
                    val activeClientRental = lifecycleRental?.let { activeClientRentalForLifecycle(it, store, LocalDate.now()) }
                    when {
                        clientRental != null -> clientRental.comment = comment
                        activeClientRental != null -> activeClientRental.comment = comment
                        lifecycleRental != null -> lifecycleRental.comment = comment
                    }
                    persistState()
                }
                call.respond(
                    ApiAdminRentalCommentUpdateResponse(
                        rentalId = rentalId,
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

                val lifecycleRental = store.rentals.firstOrNull { it.id == rentalId }
                val clientRental = store.clientRentals.firstOrNull { it.id == rentalId }
                val ownerAdminId = lifecycleRental?.adminId ?: clientRental?.adminId
                if (lifecycleRental == null && clientRental == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }
                if (ownerAdminId != session.userId) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
                    return@post
                }

                val request = call.receive<ApiAdminRentalLinksUpdateRequest>()
                synchronized(stateLock) {
                    val videoUrl = request.videoUrl?.trim()?.ifBlank { null }
                    val contractUrl = request.contractUrl?.trim()?.ifBlank { null }
                    val activeClientRental = lifecycleRental?.let { activeClientRentalForLifecycle(it, store, LocalDate.now()) }
                    when {
                        clientRental != null -> {
                            clientRental.videoUrl = videoUrl
                            clientRental.contractUrl = contractUrl
                        }
                        activeClientRental != null -> {
                            activeClientRental.videoUrl = videoUrl
                            activeClientRental.contractUrl = contractUrl
                        }
                        lifecycleRental != null -> {
                            lifecycleRental.videoUrl = videoUrl
                            lifecycleRental.contractUrl = contractUrl
                        }
                    }
                    persistState()
                }

                val responseVideoUrl = clientRental?.videoUrl
                    ?: lifecycleRental?.let { activeClientRentalForLifecycle(it, store, LocalDate.now())?.videoUrl ?: it.videoUrl }
                val responseContractUrl = clientRental?.contractUrl
                    ?: lifecycleRental?.let { activeClientRentalForLifecycle(it, store, LocalDate.now())?.contractUrl ?: it.contractUrl }
                call.respond(
                    ApiAdminRentalLinksUpdateResponse(
                        rentalId = rentalId,
                        videoUrl = responseVideoUrl,
                        contractUrl = responseContractUrl
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
                        activeClientRentalForLifecycle(currentRental, store, LocalDate.now())?.let { activeClientRental ->
                            val clientRentalIndex = store.clientRentals.indexOfFirst { it.id == activeClientRental.id }
                            if (clientRentalIndex >= 0) {
                                store.clientRentals[clientRentalIndex] = activeClientRental.copy(
                                    bikeId = bike.id,
                                    startDate = periodStart,
                                    endDate = periodEnd
                                )
                            }
                        }
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

            post("/admin/rentals/{rentalId}/pipeline-status") {
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

                val request = call.receive<ApiAdminUpdateRentalPipelineStatusRequest>()
                val pipelineStatus = RentalPipelineStatus.fromApi(request.pipelineStatus.trim())
                if (pipelineStatus == null) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "pipeline_status is invalid"))
                    return@post
                }

                val updated = synchronized(stateLock) {
                    val rentalIndex = store.rentals.indexOfFirst { it.id == rentalId }
                    if (rentalIndex < 0) {
                        null
                    } else {
                        val current = store.rentals[rentalIndex]
                        if (current.adminId != session.userId) {
                            return@synchronized null
                        }
                        val next = if (pipelineStatus == RentalPipelineStatus.IN_STOCK) {
                            transitionRentalToInStock(store, rentalIndex, LocalDate.now())
                        } else {
                            current.copy(pipelineStatus = pipelineStatus)
                        }
                        store.rentals[rentalIndex] = next
                        persistState()
                        next
                    }
                }

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiAdminUpdateRentalPipelineStatusResponse(
                        rentalId = updated.id,
                        pipelineStatus = RentalPipelineStatus.toApi(updated.pipelineStatus)
                    )
                )
            }

            post("/admin/rentals/{rentalId}/finish") {
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

                val today = LocalDate.now()
                val updated = synchronized(stateLock) {
                    val rentalIndex = store.rentals.indexOfFirst { it.id == rentalId }
                    if (rentalIndex < 0) {
                        null
                    } else {
                        val current = store.rentals[rentalIndex]
                        if (current.adminId != session.userId) {
                            return@synchronized null
                        }
                        val next = transitionRentalToInStock(store, rentalIndex, today)
                        persistState()
                        next
                    }
                }

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiAdminFinishRentalResponse(
                        rentalId = updated.id,
                        periodEnd = updated.endDate?.toString() ?: today.toString()
                    )
                )
            }

            post("/admin/rentals/{rentalId}/client-rentals") {
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

                val request = call.receive<ApiAdminStartClientRentalRequest>()
                when (
                    val result = startClientRentalInExistingRental(
                        store = store,
                        stateLock = stateLock,
                        persistState = persistState,
                        adminId = session.userId,
                        rentalId = rentalId,
                        request = request
                    )
                ) {
                    is StartClientRentalOutcome.Failure ->
                        call.respond(result.status, ApiErrorResponse(message = result.message))

                    is StartClientRentalOutcome.Success ->
                        call.respond(HttpStatusCode.OK, result.response)
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
                    if (normalizeStoreState()) {
                        saveState()
                    }

                    val lifecycleIndex = findLifecycleRentalIndexForDeletion(
                        store = store,
                        adminId = session.userId,
                        rentalId = rentalId
                    )
                    if (lifecycleIndex < 0) {
                        false
                    } else {
                        deleteLifecycleRental(store, lifecycleIndex, LocalDate.now())
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
                        val createdPayment = paymentService.createPayment(
                            clientId = session.clientId,
                            paymentType = type,
                            rentalId = session.rentalId
                        )
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

                if (session.role == Role.CLIENT) {
                    if (session.clientId != payment.clientId) {
                        call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
                        return@get
                    }
                    if (session.rentalId != null && payment.rentalId != null && session.rentalId != payment.rentalId) {
                        call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(message = "Forbidden"))
                        return@get
                    }
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
