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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    val carriedDebtRub: Int = 0,
    val comment: String? = null
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
    val phones: List<ApiAdminClientPhoneResponse> = emptyList(),
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
    val batterySerialNumber2: String? = null,
    /**
     * true ⇔ к велосипеду сейчас привязана НЕ-удалённая lifecycle-аренда.
     * Используется в iOS-пикерах для фильтрации свободных велосипедов
     * без надобности отдельно запрашивать /admin/rents.
     */
    @SerialName("bike_is_in_rental")
    val bikeIsInRental: Boolean = false
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
    val periodEnd: String? = null,
    val login: String? = null,
    val password: String? = null,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null,
    val comment: String? = null
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
    val deleted: Boolean,
    @SerialName("delete_kind")
    val deleteKind: String
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

/**
 * Журнал клиента — те же записи, что админ видит для client_rental.
 * Возвращается из GET /client/me/ledger для текущей client_rental клиента
 * (docs/04_api_draft.md «Client», docs/01_scope_freeze.md §2 «платежи отображаются в журнале»).
 */
@Serializable
private data class ApiClientLedgerEntryResponse(
    val type: String,
    @SerialName("amount_rub")
    val amountRub: Int,
    @SerialName("created_at")
    val createdAt: String,
    val note: String? = null
)

@Serializable
private data class ApiClientLedgerResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("entries")
    val entries: List<ApiClientLedgerEntryResponse>
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
    val journalEntries: List<ApiAdminRentalJournalEntryResponse>,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null,
    val comment: String? = null
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
    // Сначала пытаемся найти именно тот lifecycle, к которому client_rental
    // была привязана, даже если он сейчас soft-deleted — для отображения
    // исторической карточки в админке (delete не должен сломать просмотр истории).
    val byId = store.rentals.firstOrNull { it.id == clientRental.rentalId }
    if (byId != null) return byId
    // Fallback: ищем «живой» lifecycle для того же велосипеда и админа
    // (используется в legacy-нормализации client_rental→lifecycle).
    return store.rentals.firstOrNull {
        it.bikeId == clientRental.bikeId && it.adminId == clientRental.adminId && it.deletedAt == null
    }
}

private fun activeClientRentalForLifecycle(
    rental: RentalRecord,
    store: InMemoryStore,
    asOf: LocalDate
): ClientRentalRecord? {
    // У удалённой lifecycle-аренды нет «активной» клиентской: запись в архиве,
    // но client_rentals могут существовать (история). Возвращаем null.
    if (rental.deletedAt != null) return null
    return store.clientRentals
        .asSequence()
        .filter { it.rentalId == rental.id }
        .filter { it.adminId == rental.adminId }
        .filter { it.deletedAt == null }
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
        .filter { it.deletedAt == null }
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

    // Группируем «живые» lifecycle-аренды по (adminId, bikeId) — soft-deleted
    // не участвуют в нормализации, но и не удаляются физически. Это сохраняет
    // инвариант «один bike — одна неудалённая lifecycle» и при этом не теряет
    // историю удалённых аренд.
    val lifecycleByGroup = store.rentals
        .asSequence()
        .filter { it.deletedAt == null }
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

    // Legacy-дубликаты lifecycle на один и тот же (adminId, bikeId), которые
    // НЕ попали в lifecycleByGroup как «канонический», помечаются soft-delete'ом.
    // Физически не удаляем — история сохраняется. До soft-delete фичи здесь был
    // removeAll, что приводило к потере данных при дубликатах.
    val canonicalLifecycleIds = lifecycleByGroup.values.map { it.id }.toSet()
    val now = java.time.Instant.now()
    store.rentals.replaceAll { rental ->
        if (rental.deletedAt == null && rental.id !in canonicalLifecycleIds) {
            // Этот lifecycle — duplicate для группы (admin, bike), его «жизненный» собрат
            // уже выбран. Помечаем soft-deleted.
            changed = true
            rental.copy(deletedAt = now)
        } else {
            rental
        }
    }

    // Инвариант: у каждой ClientRentalRecord должны быть непустые
    // clientLogin и clientPassword (docs/14_rental_lifecycle.md §4).
    // Если запись пришла из legacy-миграции с пустыми credentials или из
    // seed-данных без явных логина/пароля — пробуем заполнить:
    //   1) из исходной RentalRecord.clientLogin/Password (старый источник);
    //   2) из AppUser клиента (последний известный логин/пароль).
    // Этот шаг идемпотентен: запись с уже заполненными credentials не меняется.
    // Бэкфилл fingerprint для тех ClientRentalRecord, где он пустой
    // (legacy-данные до фичи уникальности). Делается ДО backfill credentials,
    // чтобы случай «и login пустой, и fingerprint пустой» обработался единообразно
    // после следующего шага: после backfill credentials fingerprint посчитается ниже.
    store.clientRentals.replaceAll { clientRental ->
        if (clientRental.clientPasswordFingerprint.isNotBlank()) return@replaceAll clientRental
        if (clientRental.clientPassword.isBlank()) return@replaceAll clientRental
        changed = true
        clientRental.copy(clientPasswordFingerprint = passwordFingerprint(clientRental.clientPassword))
    }

    // Бэкфилл credentials для legacy-данных. ВАЖНО — fallback только из
    // RentalRecord (исходная legacy-запись, где login/password жили до перехода
    // на ClientRentalRecord). AppUser.login/password НЕ используются как
    // fallback, потому что AppUser перезаписывается при каждом старте новой
    // client_rental — старые historical client_rentals при этом потеряли бы
    // свои оригинальные credentials, получив credentials последней аренды.
    // Этот баг проявлялся как «у других клиентов закрытые аренды видны
    // с логином/паролем, а у Ивана Петрова — прочерки», потому что Иван имел
    // несколько последовательных аренд, и AppUser у него обновился.
    store.clientRentals.replaceAll { clientRental ->
        val needsLogin = clientRental.clientLogin.isBlank()
        val needsPassword = clientRental.clientPassword.isBlank()
        if (!needsLogin && !needsPassword) return@replaceAll clientRental

        val legacyRental = store.rentals.firstOrNull { it.id == clientRental.rentalId }
        val backfilledLogin = clientRental.clientLogin.ifBlank {
            legacyRental?.clientLogin?.takeIf { it.isNotBlank() } ?: ""
        }
        val backfilledPassword = clientRental.clientPassword.ifBlank {
            legacyRental?.clientPassword?.takeIf { it.isNotBlank() } ?: ""
        }
        if (backfilledLogin == clientRental.clientLogin && backfilledPassword == clientRental.clientPassword) {
            clientRental
        } else {
            changed = true
            // Если пароль появился после backfill — синхронизируем fingerprint.
            val backfilledFingerprint = if (backfilledPassword.isNotBlank() && clientRental.clientPasswordFingerprint.isBlank()) {
                passwordFingerprint(backfilledPassword)
            } else {
                clientRental.clientPasswordFingerprint
            }
            clientRental.copy(
                clientLogin = backfilledLogin,
                clientPassword = backfilledPassword,
                clientPasswordFingerprint = backfilledFingerprint
            )
        }
    }

    store.rentals.replaceAll { rental ->
        // Soft-deleted lifecycle нормализации не подвергаем — они остаются
        // в архиве с теми полями, которые были на момент удаления.
        if (rental.deletedAt != null) return@replaceAll rental

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

/**
 * Защита от попадания тяжёлых data:base64 картинок в list-эндпоинты.
 * Если бэкенд хранит фото велосипеда как `data:image/jpeg;base64,...` (а так
 * делает текущий iOS upload), один URL может быть 2 МБ. Дублируясь в
 * /admin/rents (по 1 на rental), /admin/clients (по 1 на клиента) и
 * /admin/clients/{id}.rentals (по 1 на каждую history-item) — ответ раздувается
 * до десятков МБ и iOS/KMP не успевают парсить в таймаут.
 *
 * Список-эндпоинты отдают пустую строку для таких URL — клиент использует
 * placeholder. Полное фото доступно через /admin/bikes (там URL не сокращается).
 *
 * Cutoff 1024 символа — http(s) URLs практически всегда короче, base64-картинки
 * почти всегда длиннее.
 */
private fun compactBikeAvatarUrl(url: String?): String {
    if (url.isNullOrEmpty()) return ""
    if (url.startsWith("data:")) return ""
    if (url.length > 1024) return ""
    return url
}

private fun bikeToApiResponse(bike: BikeAccount, isInRental: Boolean = false): ApiAdminBikeResponse {
    return ApiAdminBikeResponse(
        bikeId = bike.id,
        photoUrl = bike.photoUrl,
        bikeModel = bike.model,
        weeklyRateRub = bike.weeklyRateRub,
        frameSerialNumber = bike.frameSerialNumber,
        motorSerialNumber = bike.motorSerialNumber,
        batterySerialNumber1 = bike.batterySerialNumber1,
        batterySerialNumber2 = bike.batterySerialNumber2,
        bikeIsInRental = isInRental
    )
}

/**
 * Заглушка для случаев одиночного bike в ответе на create/update —
 * подсчёт isInRental по текущему стору. Не использовать в горячих циклах
 * (для списка велосипедов считаем bike_id-set один раз).
 */
private fun bikeToApiResponse(bike: BikeAccount, store: InMemoryStore): ApiAdminBikeResponse {
    val inRental = store.rentals.any {
        it.bikeId == bike.id && it.adminId == bike.adminId && it.deletedAt == null
    }
    return bikeToApiResponse(bike, isInRental = inRental)
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
        // Soft-delete: серийник освобождается, когда bike помечен deletedAt.
        // Это даёт админу возможность переиспользовать серийник (например,
        // если велосипед физически списан, и появляется новый под тем же номером).
        val duplicate = store.bikes.firstOrNull { bike ->
            bike.id != bikeIdToIgnore &&
                bike.deletedAt == null &&
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

/**
 * Технический отпечаток пароля для проверки неповторяемости
 * (docs/14_rental_lifecycle.md §4). SHA-256 hex от UTF-8 байт пароля.
 * При сравнении пароли сначала trim'ятся — пробелы по краям не отличают пароли.
 */
private fun passwordFingerprint(password: String): String {
    val normalized = password.trim()
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Проверяет, что fingerprint пароля не встречается ни в одной живой
 * (не soft-deleted) ClientRentalRecord. Возвращает true если уникален.
 * `ignoreClientRentalId` нужен для случаев апдейта (когда сравнение со
 * своей же записью не должно считаться коллизией).
 */
private fun isPasswordFingerprintUnique(
    store: InMemoryStore,
    fingerprint: String,
    ignoreClientRentalId: String? = null
): Boolean {
    if (fingerprint.isBlank()) return true
    return store.clientRentals.none { record ->
        record.id != ignoreClientRentalId &&
            record.deletedAt == null &&
            record.clientPasswordFingerprint == fingerprint
    }
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

    val client = store.clients.firstOrNull { it.id == clientId && it.deletedAt == null }
        ?: return RentalCreationOutcome.Failure(HttpStatusCode.NotFound, "Client not found")
    val bike = store.bikes.firstOrNull { it.id == bikeId && it.deletedAt == null }
        ?: return RentalCreationOutcome.Failure(HttpStatusCode.NotFound, "Bike not found")
    if (!adminOwnsClient(store, adminId, client.id)) {
        return RentalCreationOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
    }
    if (!adminOwnsBike(store, adminId, bike.id)) {
        return RentalCreationOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
    }
    // Soft-delete: инвариант «один bike — одна неудалённая lifecycle» проверяется
    // только среди живых записей. Удалённые lifecycle для того же велосипеда
    // остаются в store как историческая запись и не блокируют новую аренду.
    if (store.rentals.any { it.bikeId == bike.id && it.adminId == adminId && it.deletedAt == null }) {
        return RentalCreationOutcome.Failure(HttpStatusCode.Conflict, "bike already has rental")
    }
    // Инвариант: пароль клиентской аренды должен быть уникальным (docs/14_rental_lifecycle.md §4).
    val newPasswordFingerprint = passwordFingerprint(password)
    if (!isPasswordFingerprintUnique(store, newPasswordFingerprint)) {
        return RentalCreationOutcome.Failure(HttpStatusCode.Conflict, "password is already used")
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
            taxMode = taxMode,
            clientPasswordFingerprint = newPasswordFingerprint
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
        val rentalIndex = store.rentals.indexOfFirst { it.id == rentalId && it.deletedAt == null }
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

        // Инвариант уникальности пароля (docs/14_rental_lifecycle.md §4) —
        // проверяем перед мутацией state.
        val newPasswordFingerprint = passwordFingerprint(password)
        if (!isPasswordFingerprintUnique(store, newPasswordFingerprint)) {
            return@synchronized StartClientRentalOutcome.Failure(HttpStatusCode.Conflict, "password is already used")
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
            taxMode = currentRental.taxMode,
            clientPasswordFingerprint = newPasswordFingerprint
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
    // Soft-delete: запись остаётся в store с deletedAt != null, перестаёт показываться
    // в списочных эндпоинтах и валидациях уникальности, но клиентская история
    // (ClientRentalRecord, ledger, payments) сохраняется и доступна.
    // Перечитываем current из store, потому что выше мы могли мутировать
    // store.clients (carriedDebt) — для самого `current: RentalRecord` это
    // не критично, но используем тот же индекс для консистентности.
    val rentalToMark = store.rentals[rentalIndex]
    store.rentals[rentalIndex] = rentalToMark.copy(deletedAt = rentalToMark.deletedAt ?: java.time.Instant.now())
}

private fun findLifecycleRentalIndexForDeletion(
    store: InMemoryStore,
    adminId: String,
    rentalId: String
): Int {
    val lifecycleIndex = store.rentals.indexOfFirst {
        it.id == rentalId && it.adminId == adminId && it.deletedAt == null
    }
    if (lifecycleIndex >= 0) return lifecycleIndex

    val clientRental = store.clientRentals.firstOrNull { it.id == rentalId && it.adminId == adminId }
        ?: return -1
    // Не находим повторно уже удалённый lifecycle — фильтруем по deletedAt == null.
    return store.rentals.indexOfFirst {
        it.id == clientRental.rentalId && it.adminId == adminId && it.deletedAt == null
    }
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
    val debt = calculateClientTotalDebtRub(
        client = client,
        store = store,
        asOf = now,
        adminId = adminId
    )
    val paid = LedgerCalculator.totalPaidRub(store.ledger, client.id, snapshot?.clientRentalId)
    // Прибыль = всё, что клиент фактически оплатил по этой аренде. Раньше
    // было `if (debt == 0) paid else 0` — это давало 0 для любой аренды
    // с непогашенным долгом (например при возврате с перерасходом
    // client заплатил 9000₽ и должен ещё 7000₽ — прибыль admin'a 9000₽, не 0).
    // Долг и прибыль — независимые суммы, а не альтернативы.
    val profit = paid
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
        bikeAvatarUrl = compactBikeAvatarUrl(snapshot?.bikePhotoUrl),
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

/**
 * Суммарный долг клиента по всем его client_rental (активным и закрытым),
 * которые не soft-deleted.
 *
 * Важный инвариант для фильтра "Должники" на iOS:
 * неактивный клиент с долгом по закрытой client_rental всё равно должен
 * оставаться должником, даже если carriedDebt уже списан/обнулён.
 */
private fun calculateClientTotalDebtRub(
    client: ClientAccount,
    store: InMemoryStore,
    asOf: LocalDate,
    adminId: String? = null
): Int {
    val bikesById = store.bikes.associateBy { it.id }
    val ledgerByClientRental = store.ledger.groupBy { it.rentalId ?: "" }

    return store.clientRentals
        .asSequence()
        .filter { it.clientId == client.id }
        .filter { adminId == null || it.adminId == adminId }
        .filter { it.deletedAt == null }
        .sumOf { rental ->
            val weeklyRateRub = bikesById[rental.bikeId]?.weeklyRateRub ?: 0
            if (weeklyRateRub <= 0) {
                return@sumOf 0
            }
            val rentalLedger = ledgerByClientRental[rental.id] ?: emptyList()
            if (rental.endDate != null) {
                LedgerCalculator.finalDebtOnClosure(
                    clientId = client.id,
                    rentalStartDate = rental.startDate,
                    rentalEndDate = rental.endDate,
                    weeklyRateRub = weeklyRateRub,
                    entries = rentalLedger,
                    rentalId = rental.id
                )
            } else {
                LedgerCalculator.debtRub(
                    clientId = client.id,
                    rentalStartDate = rental.startDate,
                    weeklyRateRub = weeklyRateRub,
                    entries = rentalLedger,
                    asOf = asOf,
                    rentalId = rental.id
                )
            }
        }
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
        bikeAvatarUrl = compactBikeAvatarUrl(bike?.photoUrl),
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
    // Индексы для O(1) lookup'ов и однократной группировки ledger.
    // Раньше для каждой из N клиентских аренд:
    //   - линейный поиск bike по bikeId      — O(|bikes|)
    //   - LedgerCalculator.totalPaidRub      — итерация по всему ledger
    //   - LedgerCalculator.totalAdjustmentRub — ещё итерация
    //   - LedgerCalculator.finalDebtOnClosure / debtRub — ещё итерация (вызывает totalPaidRub и totalAdjustmentRub)
    // Итого ~5 проходов по ledger на аренду × N аренд. С 6 арендами и
    // несколькими сотнями entries это ощутимо тормозило загрузку карточки.
    // Сейчас группируем ledger по rentalId один раз; bikes — в Map.
    val bikesById: Map<String, BikeAccount> = store.bikes.associateBy { it.id }
    val ledgerByClientRental: Map<String, List<LedgerEntry>> =
        store.ledger.groupBy { it.rentalId ?: "" }

    val rentals = store.clientRentals
        .asSequence()
        .filter { it.clientId == client.id }
        .filter { adminId == null || it.adminId == adminId }
        .filter { it.deletedAt == null }  // не показываем soft-deleted client_rentals в истории
        .sortedByDescending { it.startDate }
        .map {
            val bike = bikesById[it.bikeId]
            val weeklyRateRub = bike?.weeklyRateRub ?: 0
            val rentalAsOf = it.endDate ?: now
            val rentalLedger = ledgerByClientRental[it.id] ?: emptyList()
            val rentalPaidRub = LedgerCalculator.totalPaidRub(rentalLedger, client.id, it.id)
            val rentalAdjustmentRub = LedgerCalculator.totalAdjustmentRub(rentalLedger, client.id, it.id)
            // Долг закрытой client_rental считается строго по дням
            // (docs/02_money_and_debt_rules.md §5, docs/14_rental_lifecycle.md §3) —
            // одинаково и при /finish (переход в in_stock), и при /delete.
            // Для активной client_rental по-прежнему используется per-week
            // (debtRub), т.к. начисление идёт неделями.
            val rentalDebtRub = if (weeklyRateRub > 0) {
                if (it.endDate != null) {
                    LedgerCalculator.finalDebtOnClosure(
                        clientId = client.id,
                        rentalStartDate = it.startDate,
                        rentalEndDate = it.endDate,
                        weeklyRateRub = weeklyRateRub,
                        entries = rentalLedger,
                        rentalId = it.id
                    )
                } else {
                    LedgerCalculator.debtRub(
                        clientId = client.id,
                        rentalStartDate = it.startDate,
                        weeklyRateRub = weeklyRateRub,
                        entries = rentalLedger,
                        asOf = rentalAsOf,
                        rentalId = it.id
                    )
                }
            } else {
                0
            }
            // Картинку велосипеда (`bikeAvatarUrl`) НЕ дублируем как data:base64:
            // фото у пользователя может храниться как `data:image/jpeg;base64,...`
            // (несколько МБ), и при 8 арендах того же велосипеда ответ
            // раздувался до 19+ МБ. compactBikeAvatarUrl пропускает только
            // короткие http(s) URLs. iOS-сторона для data:base64 фоток
            // отвалится на placeholder; полное фото подтянет из /admin/bikes.
            ApiAdminRentalHistoryItemResponse(
                rentalId = it.id,
                bikeId = it.bikeId,
                bikeAvatarUrl = compactBikeAvatarUrl(bike?.photoUrl),
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
        bikeAvatarUrl = compactBikeAvatarUrl(snapshot?.bikePhotoUrl),
        rentalStart = snapshot?.rentalStartDate?.toString() ?: "",
        paidUntil = paidUntil,
        totalPaidRub = totalPaid,
        debtRub = debt,
        totalAdjustmentRub = totalAdjustment,
        phones = client.phones.map { ApiAdminClientPhoneResponse(label = it.label, number = it.number) },
        rentals = rentals,
        carriedDebtRub = client.carriedDebtRub,
        comment = client.comment
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
                    // В response отдаём короткое имя класса + сообщение —
                    // помогает быстро диагностировать причину 500 без доступа к
                    // backend-логам. Stack trace НЕ отдаём (мог бы утечь PII / пути).
                    val cls = cause::class.simpleName ?: "Throwable"
                    val msg = cause.message?.take(200) ?: "(no message)"
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiErrorResponse(message = "internal server error: $cls — $msg")
                    )
                }
            }
        }
    }

    val useInMemory = System.getenv("ATOMGO_USE_INMEMORY")?.equals("true", ignoreCase = true) == true
    val stateStore = if (useInMemory) null else PostgresStateStore.fromEnvironment()
    val store = stateStore?.loadOrInitialize(InMemoryStore.seed()) ?: InMemoryStore.seed()
    val stateLock = Any()

    // Debounced async save: на каждую мутацию запускаем фоновый сейв,
    // отменяем предыдущий неуспевший. Postgres save (DELETE+INSERT всех таблиц
    // + encode legacy JSON) — это десятки-сотни ms даже на быстром Postgres,
    // и раньше мы блокировали HTTP-handler до его завершения. Если admin
    // подряд делал мутацию + read другого ресурса, read мог получить
    // request_timeout (5s на iOS), потому что handler-поток или JDBC-pool
    // были заняты предыдущим save.
    //
    // Теперь: handler возвращает ответ клиенту сразу. Save идёт в IO-coroutine
    // с задержкой 250ms — если за это время приходит ещё мутация, предыдущий
    // job отменяется и save сдвигается. Это батчирует сразу несколько мутаций
    // в один Postgres-trip.
    //
    // Trade-off: при крэше backend в окне 250ms можно потерять последнюю
    // мутацию. Для MVP терпимо. На graceful shutdown добавлен flush ниже.
    val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var pendingSaveJob: Job? = null
    val saveLog = environment.log

    // Снимок in-memory store для последующей записи в Postgres.
    // ВАЖНО: snapshot должен сниматься ВНУТРИ stateLock (чтобы избежать CME при
    // конкуррентной мутации), но сама запись в Postgres идёт ВНЕ stateLock —
    // иначе любой read-endpoint, использующий stateLock, заблокируется на
    // сотни ms (Postgres DELETE+INSERT всех таблиц). Именно это давало
    // request_timeout на iOS при простом GET /admin/clients/{id}.
    //
    // toMutableList() / ConcurrentHashMap(map) делают shallow copy: списки
    // и map становятся новыми контейнерами, но элементы — это immutable data
    // class'ы с val-полями, так что shallow safe.
    fun snapshotStore(): InMemoryStore = synchronized(stateLock) {
        InMemoryStore(
            users = store.users.toMutableList(),
            clients = store.clients.toMutableList(),
            bikes = store.bikes.toMutableList(),
            rentals = store.rentals.toMutableList(),
            clientRentals = store.clientRentals.toMutableList(),
            ledger = store.ledger.toMutableList(),
            payments = store.payments.toMutableList(),
            sessions = java.util.concurrent.ConcurrentHashMap(store.sessions),
            processedWebhookEvents = store.processedWebhookEvents.toMutableSet()
        )
    }

    val saveState: () -> Unit = saveState@{
        val nonNullStore = stateStore ?: return@saveState
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            try {
                delay(250)
                // Snapshot быстро (миллисекунды), запись в Postgres — снаружи lock'а.
                val snapshot = snapshotStore()
                nonNullStore.save(snapshot)
            } catch (_: CancellationException) {
                // Новая мутация подвинула save — это OK, следующий job сохранит свежее состояние.
            } catch (e: Throwable) {
                saveLog.error("Failed to save backend state to Postgres", e)
            }
        }
    }
    val saveStateSync: () -> Unit = saveStateSync@{
        // Синхронный flush — startup (после первичной нормализации) и shutdown.
        val nonNullStore = stateStore ?: return@saveStateSync
        val snapshot = snapshotStore()
        nonNullStore.save(snapshot)
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
        saveStateSync()
    }
    // Финальный flush на shutdown — гарантирует что последний save не пропадёт.
    environment.monitor.subscribe(io.ktor.server.application.ApplicationStopping) {
        try {
            pendingSaveJob?.let { runBlocking { it.join() } }
        } catch (_: Throwable) { /* ignore */ }
        try {
            saveStateSync()
        } catch (e: Throwable) {
            saveLog.error("Failed to flush state on shutdown", e)
        }
        saveScope.cancel()
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
                // Долг закрытой client_rental — per-day (см. docs/02_money_and_debt_rules.md §5).
                val isClosed = snapshot != null && !snapshot.isActive && snapshot.rentalEndDate != null
                val closedRentalDebt = if (isClosed && snapshot != null) {
                    LedgerCalculator.finalDebtOnClosure(
                        clientId = client.id,
                        rentalStartDate = snapshot.rentalStartDate,
                        rentalEndDate = snapshot.rentalEndDate!!,
                        weeklyRateRub = snapshot.weeklyRateRub,
                        entries = store.ledger,
                        rentalId = snapshot.clientRentalId
                    )
                } else 0
                val debt = if (isClosed) closedRentalDebt else (projection?.debtRub ?: 0)
                val paidUntil = projection?.paidUntilDate?.toString() ?: ""
                val balanceRub = projection?.balanceRub ?: 0
                val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id, snapshot?.clientRentalId)
                val weeklyRate = snapshot?.weeklyRateRub ?: 0

                // Презеты payment-кнопок (docs/14_rental_lifecycle.md §3, scope_freeze §2):
                // — для АКТИВНОЙ аренды показываем нормальные суммы (платёж добавляет
                //   к paid_until);
                // — для ЗАКРЫТОЙ аренды клиент может только погашать долг. Кнопка,
                //   чья сумма больше остаточного долга, обнуляется (UI её
                //   делает disabled). debt_exact_rub всегда = остатку долга.
                val dayBase = PricingRules.dayAmount(weeklyRate)
                val weekBase = PricingRules.weekAmount(weeklyRate)
                val twoWeeksBase = PricingRules.twoWeeksAmount(weeklyRate)
                val monthBase = PricingRules.monthAmount(weeklyRate)
                val presets = if (isClosed) {
                    fun clamp(amount: Int) = if (amount in 1..debt) amount else 0
                    ApiClientPaymentPresetsResponse(
                        dayRub = clamp(dayBase),
                        weekRub = clamp(weekBase),
                        twoWeeksRub = clamp(twoWeeksBase),
                        monthRub = clamp(monthBase),
                        debtExactRub = debt
                    )
                } else {
                    ApiClientPaymentPresetsResponse(
                        dayRub = dayBase,
                        weekRub = weekBase,
                        twoWeeksRub = twoWeeksBase,
                        monthRub = monthBase,
                        debtExactRub = debt
                    )
                }

                call.respond(
                    ApiClientDashboardResponse(
                        clientId = client.id,
                        bikeModel = snapshot?.bikeModel ?: "",
                        bikeAvatarUrl = compactBikeAvatarUrl(snapshot?.bikePhotoUrl),
                        rentalStart = snapshot?.rentalStartDate?.toString() ?: "",
                        paidUntil = paidUntil,
                        completedAt = snapshot?.rentalEndDate?.toString(),
                        rentalIsActive = snapshot?.isActive == true,
                        debtRub = debt,
                        balanceRub = balanceRub,
                        totalAdjustmentRub = totalAdjustment,
                        presets = presets,
                        taxMode = (snapshot?.taxMode ?: AdminTaxMode.SELF_EMPLOYED).name.lowercase(),
                        requiresReceiptEmail = snapshot?.taxMode == AdminTaxMode.INDIVIDUAL_ENTREPRENEUR &&
                            !clientHasReceiptEmail(client),
                        receiptEmail = extractClientReceiptEmail(client)
                    )
                )
            }

            /**
             * Журнал текущей client_rental клиента
             * (docs/04_api_draft.md, docs/01_scope_freeze.md §2).
             * Источник записей — store.ledger, отфильтрованный по client_rental_id
             * из сессии клиента. Структура входов идентична admin-журналу.
             */
            get("/client/me/ledger") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.CLIENT || session.clientId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@get
                }

                val rentalId = session.rentalId
                if (rentalId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiClientLedgerResponse(rentalId = "", entries = emptyList())
                    )
                    return@get
                }

                // Журнал клиента — СТРОГО его текущей client_rental
                // (docs/01_scope_freeze.md §2). carriedDebt-операции и legacy-entries
                // без rentalId — это история клиента, не показываются здесь.
                val entries = store.ledger
                    .asSequence()
                    .filter { entry -> entry.rentalId == rentalId }
                    .sortedByDescending { it.createdAt }
                    .map { entry ->
                        ApiClientLedgerEntryResponse(
                            type = entry.type.name.lowercase(),
                            amountRub = ledgerSignedAmountForUi(entry),
                            createdAt = entry.createdAt.toString(),
                            note = entry.note
                        )
                    }
                    .toList()

                call.respond(HttpStatusCode.OK, ApiClientLedgerResponse(rentalId = rentalId, entries = entries))
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
                    .asSequence()
                    .filter { client -> client.deletedAt == null }
                    .filter { client -> adminOwnsClient(store, session.userId, client.id) }
                    .map { client -> buildAdminClientSummary(client, store, now, session.userId) }
                    .toList()
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
                    // normalizeStoreState() убран из read-only пути — он вызывается
                    // один раз на старте сервиса и затем persistState() при каждой
                    // мутации. На GET он только добавлял задержку.
                    store.rentals
                        .asSequence()
                        .filter { rental -> rental.adminId == session.userId }
                        .filter { rental -> rental.deletedAt == null }
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
                    // normalize не нужен на чтении — см. /admin/rents endpoint выше.
                    val lifecycleRental = store.rentals.firstOrNull { it.id == rentalId && it.adminId == session.userId && it.deletedAt == null }
                    val targetClientRental = if (lifecycleRental != null) {
                        activeClientRentalForLifecycle(lifecycleRental, store, now)
                    } else {
                        // Удалённая client_rental — не открываем (она исчезла из истории
                        // по запросу админа). Soft-delete сохраняет данные в БД,
                        // но интерактивный просмотр заблокирован.
                        store.clientRentals.firstOrNull { it.id == rentalId && it.adminId == session.userId && it.deletedAt == null }
                    }
                    val rental = lifecycleRental ?: targetClientRental?.let { lifecycleRentalForClientRental(it, store) }
                    val bikeId = targetClientRental?.bikeId ?: rental?.bikeId ?: return@synchronized null
                    val bike = store.bikes.firstOrNull { it.id == bikeId } ?: return@synchronized null
                    val client = targetClientRental?.let { store.clients.firstOrNull { client -> client.id == it.clientId } }
                    val rentalIsActive = targetClientRental?.isActiveAt(now) == true
                    // Долг для отображения деталей: для активной — per-week через
                    // billingProjection; для закрытой client_rental — per-day через
                    // finalDebtOnClosure (docs/02_money_and_debt_rules.md §5).
                    val projection = if (targetClientRental != null && rentalIsActive) {
                        LedgerCalculator.billingProjection(
                            clientId = targetClientRental.clientId,
                            rentalStartDate = targetClientRental.startDate,
                            weeklyRateRub = bike.weeklyRateRub,
                            entries = store.ledger,
                            asOf = now,
                            rentalId = targetClientRental.id
                        )
                    } else {
                        null
                    }
                    val closedRentalDebtRub = if (targetClientRental != null && !rentalIsActive && targetClientRental.endDate != null) {
                        LedgerCalculator.finalDebtOnClosure(
                            clientId = targetClientRental.clientId,
                            rentalStartDate = targetClientRental.startDate,
                            rentalEndDate = targetClientRental.endDate,
                            weeklyRateRub = bike.weeklyRateRub,
                            entries = store.ledger,
                            rentalId = targetClientRental.id
                        )
                    } else {
                        0
                    }
                    val credentials = targetClientRental?.let(::resolveClientRentalCredentials) ?: RentalCredentials()
                    // Журнал client_rental не зависит от текущего статуса родительской
                    // lifecycle-аренды. Раньше тут было ещё условие
                    // `lifecycleRental?.pipelineStatus == IN_STOCK → empty` — оно
                    // обнуляло историю при просмотре закрытой client_rental, чей
                    // lifecycle уже стал «у меня». По спеке (docs/14_rental_lifecycle.md §1)
                    // история платежей принадлежит client_rental и должна сохраняться.
                    val journal = if (targetClientRental == null) {
                        emptyList()
                    } else {
                        store.ledger
                            .asSequence()
                            .filter { entry -> entry.rentalId == targetClientRental.id }
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
                        debtRub = projection?.debtRub ?: closedRentalDebtRub,
                        totalAdjustmentRub = if (targetClientRental != null) {
                            LedgerCalculator.totalAdjustmentRub(store.ledger, targetClientRental.clientId, targetClientRental.id)
                        } else {
                            0
                        },
                        rentalPipelineStatus = RentalPipelineStatus.toApi(rental?.pipelineStatus ?: RentalPipelineStatus.LONG_TERM),
                        rentalIsActive = rentalIsActive,
                        journalEntries = journal,
                        videoUrl = targetClientRental?.videoUrl ?: rental?.videoUrl,
                        contractUrl = targetClientRental?.contractUrl ?: rental?.contractUrl,
                        comment = targetClientRental?.comment ?: rental?.comment
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
                // Один раз собираем bikeId, занятые активными lifecycle-арендами,
                // чтобы O(n*m) превратилось в O(n+m). bike_is_in_rental позволяет
                // iOS-пикеру фильтровать свободные велосипеды без дополнительных запросов.
                val rentedBikeIds = store.rentals
                    .asSequence()
                    .filter { it.adminId == session.userId && it.deletedAt == null }
                    .map { it.bikeId }
                    .toHashSet()
                call.respond(
                    store.bikes
                        .filter { it.adminId == session.userId && it.deletedAt == null }
                        .map { bikeToApiResponse(it, isInRental = it.id in rentedBikeIds) }
                )
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

                call.respond(HttpStatusCode.Created, bikeToApiResponse(createdBike, store))
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

                call.respond(HttpStatusCode.OK, bikeToApiResponse(updated, store))
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
                    // Ищем только «живой» велосипед — уже удалённый второй раз не удаляется.
                    val bikeIndex = store.bikes.indexOfFirst { it.id == bikeId && it.deletedAt == null }
                    if (bikeIndex < 0) {
                        HttpStatusCode.NotFound to "Bike not found"
                    } else {
                        val bike = store.bikes[bikeIndex]
                        if (bike.adminId != session.userId) {
                            HttpStatusCode.NotFound to "Bike not found"
                        } else if (store.rentals.any { it.bikeId == bikeId && it.adminId == session.userId && it.deletedAt == null }) {
                            HttpStatusCode.Conflict to "bike is used by rentals"
                        } else {
                            // Soft-delete: метим как удалённый, оставляя запись в store.
                            store.bikes[bikeIndex] = bike.copy(deletedAt = java.time.Instant.now())
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
                            phones = normalizedPhones,
                            comment = request.comment?.trim()?.ifBlank { null } ?: currentClient.comment
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
                    // Только «живой» клиент: повторное удаление — 404.
                    val clientIndex = store.clients.indexOfFirst { it.id == clientId && it.deletedAt == null }
                    if (clientIndex < 0) {
                        HttpStatusCode.NotFound to "Client not found"
                    } else {
                        val client = store.clients[clientIndex]
                        if (!adminOwnsClient(store, session.userId, client.id)) {
                            return@synchronized HttpStatusCode.NotFound to "Client not found"
                        }
                        // Удалять клиента можно только если у него нет истории
                        // client_rentals — иначе нужно редактировать профиль.
                        // Эта проверка не зависит от soft-delete: история сохраняется
                        // и не должна теряться вместе с клиентом.
                        if (store.clientRentals.any { it.clientId == clientId && it.adminId == session.userId }) {
                            return@synchronized HttpStatusCode.Conflict to "client is used by rentals"
                        }
                        // Soft-delete: помечаем клиента deletedAt, сессии очищаем,
                        // AppUser оставляем нетронутыми (logins должны продолжать работать
                        // для просмотра закрытых аренд, если такие есть).
                        store.clients[clientIndex] = client.copy(deletedAt = java.time.Instant.now())
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

            post("/admin/client-rentals/{clientRentalId}/adjustments") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(message = "Unauthorized"))
                    return@post
                }

                val clientRentalId = call.parameters["clientRentalId"]
                if (clientRentalId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "clientRentalId is required"))
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
                    val clientRental = store.clientRentals.firstOrNull {
                        it.id == clientRentalId &&
                            it.adminId == session.userId &&
                            it.deletedAt == null
                    } ?: return@synchronized null

                    val bike = store.bikes.firstOrNull { it.id == clientRental.bikeId } ?: return@synchronized null
                    val today = LocalDate.now()

                    store.ledger += LedgerEntry(
                        id = "adj-${UUID.randomUUID().toString().take(8)}",
                        clientId = clientRental.clientId,
                        type = LedgerType.ADJUSTMENT,
                        direction = direction,
                        amountRub = request.amountRub,
                        createdAt = java.time.Instant.now(),
                        note = request.comment?.trim()?.ifBlank { null },
                        rentalId = clientRental.id
                    )

                    persistState()

                    val debt = if (clientRental.isActiveAt(today)) {
                        LedgerCalculator.debtRub(
                            clientId = clientRental.clientId,
                            rentalStartDate = clientRental.startDate,
                            weeklyRateRub = bike.weeklyRateRub,
                            entries = store.ledger,
                            asOf = today,
                            rentalId = clientRental.id
                        )
                    } else if (clientRental.endDate != null) {
                        LedgerCalculator.finalDebtOnClosure(
                            clientId = clientRental.clientId,
                            rentalStartDate = clientRental.startDate,
                            rentalEndDate = clientRental.endDate,
                            weeklyRateRub = bike.weeklyRateRub,
                            entries = store.ledger,
                            rentalId = clientRental.id
                        )
                    } else {
                        0
                    }

                    ApiAdminDebtAdjustmentResponse(
                        clientId = clientRental.clientId,
                        debtRub = debt,
                        totalAdjustmentRub = LedgerCalculator.totalAdjustmentRub(
                            entries = store.ledger,
                            clientId = clientRental.clientId,
                            rentalId = clientRental.id
                        )
                    )
                }

                if (response == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Client rental not found"))
                    return@post
                }

                call.respond(response)
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
                    // normalize выполнится в persistState() ниже при Success — pre-mutation
                    // проход здесь избыточен.
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

                val lifecycleRental = store.rentals.firstOrNull { it.id == rentalId && it.deletedAt == null }
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

                val lifecycleRental = store.rentals.firstOrNull { it.id == rentalId && it.deletedAt == null }
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
                val loginRaw = request.login?.trim()?.ifBlank { null }
                val passwordRaw = request.password?.trim()?.ifBlank { null }
                val shouldUpdateCredentials = request.login != null || request.password != null

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
                if (shouldUpdateCredentials && (loginRaw == null || passwordRaw == null)) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(message = "login and password are required"))
                    return@post
                }

                val updatedRental = synchronized(stateLock) {
                    val rentalIndex = store.rentals.indexOfFirst { it.id == rentalId && it.deletedAt == null }
                    if (rentalIndex < 0) {
                        null
                    } else {
                        val bike = store.bikes.firstOrNull { it.id == bikeId }
                            ?: return@synchronized RentalCreationOutcome.Failure(HttpStatusCode.NotFound, "Bike not found")
                        val currentRental = store.rentals[rentalIndex]
                        if (currentRental.adminId != session.userId || !adminOwnsBike(store, session.userId, bike.id)) {
                            return@synchronized RentalCreationOutcome.Failure(HttpStatusCode.Forbidden, "Forbidden")
                        }
                        val newVideoUrl = request.videoUrl?.trim()?.ifBlank { null }
                        val newContractUrl = request.contractUrl?.trim()?.ifBlank { null }
                        val newComment = request.comment?.trim()?.ifBlank { null }
                        val activeClientRental = activeClientRentalForLifecycle(currentRental, store, LocalDate.now())

                        if (shouldUpdateCredentials && activeClientRental == null) {
                            return@synchronized RentalCreationOutcome.Failure(
                                HttpStatusCode.Conflict,
                                "rental has no active client rental"
                            )
                        }

                        val normalizedLogin = loginRaw
                        val normalizedPassword = passwordRaw
                        if (normalizedPassword != null && activeClientRental != null) {
                            val newFingerprint = passwordFingerprint(normalizedPassword)
                            if (!isPasswordFingerprintUnique(store, newFingerprint, ignoreClientRentalId = activeClientRental.id)) {
                                return@synchronized RentalCreationOutcome.Failure(HttpStatusCode.Conflict, "password is already used")
                            }
                        }

                        val updated = currentRental.copy(
                            bikeId = bike.id,
                            startDate = periodStart,
                            endDate = periodEnd,
                            videoUrl = newVideoUrl ?: currentRental.videoUrl,
                            contractUrl = newContractUrl ?: currentRental.contractUrl,
                            comment = newComment ?: currentRental.comment
                        )

                        activeClientRental?.let { currentClientRental ->
                            val clientRentalIndex = store.clientRentals.indexOfFirst { it.id == currentClientRental.id }
                            if (clientRentalIndex >= 0) {
                                val updatedClientRental = currentClientRental.copy(
                                    bikeId = bike.id,
                                    startDate = periodStart,
                                    endDate = periodEnd,
                                    clientLogin = normalizedLogin ?: currentClientRental.clientLogin,
                                    clientPassword = normalizedPassword ?: currentClientRental.clientPassword,
                                    videoUrl = newVideoUrl ?: currentClientRental.videoUrl,
                                    contractUrl = newContractUrl ?: currentClientRental.contractUrl,
                                    comment = newComment ?: currentClientRental.comment,
                                    clientPasswordFingerprint = normalizedPassword
                                        ?.let(::passwordFingerprint)
                                        ?: currentClientRental.clientPasswordFingerprint
                                )
                                store.clientRentals[clientRentalIndex] = updatedClientRental
                            }

                            if (normalizedLogin != null && normalizedPassword != null && currentClientRental.clientId.isNotBlank()) {
                                val clientUserIndex = store.users.indexOfFirst {
                                    it.role == Role.CLIENT && it.clientId == currentClientRental.clientId
                                }
                                if (clientUserIndex >= 0) {
                                    store.users[clientUserIndex] = store.users[clientUserIndex].copy(
                                        login = normalizedLogin,
                                        password = normalizedPassword
                                    )
                                }
                                store.sessions.entries.removeAll { it.value.clientId == currentClientRental.clientId }
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
                    val rentalIndex = store.rentals.indexOfFirst { it.id == rentalId && it.deletedAt == null }
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
                    val rentalIndex = store.rentals.indexOfFirst {
                        it.id == rentalId && it.adminId == session.userId && it.deletedAt == null
                    }.takeIf { it >= 0 } ?: run {
                        // Открытие деталей из истории клиента передает id client_rental.
                        // Для UX кнопка "Завершить" должна работать идентично:
                        // если client_rental активна, завершаем её lifecycle-аренду.
                        val activeClientRental = store.clientRentals.firstOrNull {
                            it.id == rentalId &&
                                it.adminId == session.userId &&
                                it.deletedAt == null &&
                                it.isActiveAt(today)
                        }
                        if (activeClientRental == null) {
                            -1
                        } else {
                            store.rentals.indexOfFirst {
                                it.id == activeClientRental.rentalId &&
                                    it.adminId == session.userId &&
                                    it.deletedAt == null
                            }
                        }
                    }
                    if (rentalIndex < 0) return@synchronized null
                    val next = transitionRentalToInStock(store, rentalIndex, today)
                    persistState()
                    next
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

                val deleteKind = synchronized(stateLock) {
                    // Различаем два сценария:
                    // 1. Если id — это lifecycle-аренда (карточка с главного экрана):
                    //    soft-delete lifecycle + закрытие активной client_rental
                    //    + перенос остаточного долга на клиента
                    //    (см. docs/14_rental_lifecycle.md §7).
                    // 2. Если id — это конкретная (обычно закрытая) client_rental
                    //    из истории клиента: soft-delete только этой client_rental.
                    //    lifecycle остаётся, остальные client_rentals не трогаем.
                    //    Журнал и сама запись сохраняются на сервере как требует
                    //    user (soft-delete).
                    val lifecycleIndex = store.rentals.indexOfFirst {
                        it.id == rentalId && it.adminId == session.userId && it.deletedAt == null
                    }
                    if (lifecycleIndex >= 0) {
                        deleteLifecycleRental(store, lifecycleIndex, LocalDate.now())
                        persistState()
                        "lifecycle_rental"
                    } else {
                        val crIndex = store.clientRentals.indexOfFirst {
                            it.id == rentalId && it.adminId == session.userId && it.deletedAt == null
                        }
                        if (crIndex < 0) {
                            null
                        } else {
                            val cr = store.clientRentals[crIndex]
                            val today = LocalDate.now()
                            val isActiveClientRental = cr.isActiveAt(today)
                            val lifecycleIndexByClientRental = if (isActiveClientRental) {
                                store.rentals.indexOfFirst {
                                    it.id == cr.rentalId && it.adminId == session.userId && it.deletedAt == null
                                }
                            } else {
                                -1
                            }
                            if (lifecycleIndexByClientRental >= 0) {
                                // Active client_rental удаляется как lifecycle-сущность:
                                // закрываем текущую клиентскую аренду и выводим велосипед
                                // из эксплуатации единообразно с удалением из главной карточки.
                                deleteLifecycleRental(store, lifecycleIndexByClientRental, today)
                                persistState()
                                "lifecycle_rental"
                            } else {
                                // Историческая (обычно закрытая) client_rental удаляется точечно.
                                store.clientRentals[crIndex] = cr.copy(deletedAt = java.time.Instant.now())
                                // Сессии этой client_rental — закрыть, чтобы клиент
                                // не смог по старому логину открыть удалённую запись.
                                store.sessions.entries.removeAll { it.value.rentalId == cr.id }
                                persistState()
                                "client_rental"
                            }
                        }
                    }
                }

                if (deleteKind == null) {
                    call.respond(HttpStatusCode.NotFound, ApiErrorResponse(message = "Rental not found"))
                    return@post
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiAdminDeleteRentalResponse(
                        rentalId = rentalId,
                        deleted = true,
                        deleteKind = deleteKind
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
