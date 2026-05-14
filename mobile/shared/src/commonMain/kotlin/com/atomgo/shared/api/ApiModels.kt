package com.atomgo.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    @SerialName("admin")
    ADMIN,

    @SerialName("client")
    CLIENT
}

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("access_token")
    val accessToken: String,
    val role: UserRole,
    @SerialName("user_id")
    val userId: String
)

data class AuthSession(
    val accessToken: String,
    val role: UserRole,
    val userId: String
)

@Serializable
data class ClientPaymentPresetsResponse(
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
data class ClientDashboardResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String = "",
    @SerialName("rental_start")
    val rentalStart: String,
    @SerialName("paid_until")
    val paidUntil: String,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("balance_rub")
    val balanceRub: Int = 0,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int,
    val presets: ClientPaymentPresetsResponse,
    @SerialName("tax_mode")
    val taxMode: String? = null,
    @SerialName("requires_receipt_email")
    val requiresReceiptEmail: Boolean = false
)

@Serializable
data class AdminClientSummaryResponse(
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
data class AdminClientPhone(
    val label: String,
    val number: String
)

@Serializable
data class AdminRentalHistoryItemResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("bike_id")
    val bikeId: String,
    @SerialName("bike_avatar_url")
    val bikeAvatarUrl: String,
    @SerialName("period_start")
    val periodStart: String,
    @SerialName("period_end")
    val periodEnd: String? = null,
    @SerialName("bike_model")
    val bikeModel: String,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null,
    val comment: String? = null,
    @SerialName("weekly_rate_rub")
    val weeklyRateRub: Int = 0,
    @SerialName("total_paid_rub")
    val totalPaidRub: Int = 0,
    @SerialName("debt_rub")
    val debtRub: Int = 0,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int = 0
)

@Serializable
data class AdminClientDetailsResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("address")
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
    val phones: List<AdminClientPhone>,
    val rentals: List<AdminRentalHistoryItemResponse>,
    @SerialName("carried_debt_rub")
    val carriedDebtRub: Int = 0
)

@Serializable
data class AdminCreateClientRequest(
    @SerialName("full_name")
    val fullName: String,
    @SerialName("address")
    val address: String,
    @SerialName("passport_data")
    val passportData: String,
    val phones: List<AdminClientPhone>
)

@Serializable
data class AdminUpdateClientRequest(
    @SerialName("full_name")
    val fullName: String,
    @SerialName("address")
    val address: String,
    @SerialName("passport_data")
    val passportData: String,
    val phones: List<AdminClientPhone>
)

@Serializable
data class AdminCreateRentalRequest(
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
data class AdminBikeResponse(
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
     * iOS picker свободных велосипедов фильтрует по этому полю.
     */
    @SerialName("bike_is_in_rental")
    val bikeIsInRental: Boolean = false
)

@Serializable
data class AdminCreateBikeRequest(
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
data class AdminUpdateBikeRequest(
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
data class AdminUpdateRentalRequest(
    @SerialName("bike_id")
    val bikeId: String,
    @SerialName("period_start")
    val periodStart: String,
    @SerialName("period_end")
    val periodEnd: String? = null
)

@Serializable
data class AdminDeleteRentalResponse(
    @SerialName("rental_id")
    val rentalId: String,
    val deleted: Boolean,
    @SerialName("delete_kind")
    val deleteKind: String
)

@Serializable
data class AdminDeleteClientResponse(
    @SerialName("client_id")
    val clientId: String,
    val deleted: Boolean
)

@Serializable
data class AdminDeleteBikeResponse(
    @SerialName("bike_id")
    val bikeId: String,
    val deleted: Boolean
)

@Serializable
data class AdminDebtAdjustmentRequest(
    @SerialName("amount_rub")
    val amountRub: Int,
    val sign: String,
    val comment: String? = null
)

@Serializable
data class AdminDebtAdjustmentResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("debt_rub")
    val debtRub: Int,
    @SerialName("total_adjustment_rub")
    val totalAdjustmentRub: Int
)

@Serializable
data class AdminRentalCommentUpdateRequest(
    val comment: String
)

@Serializable
data class AdminRentalCommentUpdateResponse(
    @SerialName("rental_id")
    val rentalId: String,
    val comment: String
)

@Serializable
data class AdminRentalLinksUpdateRequest(
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null
)

@Serializable
data class AdminRentalLinksUpdateResponse(
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("contract_url")
    val contractUrl: String? = null
)

@Serializable
data class CreatePaymentRequest(
    @SerialName("payment_type")
    val paymentType: String
)

@Serializable
data class UpdateClientReceiptEmailRequest(
    val email: String
)

@Serializable
data class UpdateClientReceiptEmailResponse(
    @SerialName("client_id")
    val clientId: String,
    val email: String
)

@Serializable
data class CreatePaymentResponse(
    @SerialName("payment_id")
    val paymentId: String,
    @SerialName("amount_rub")
    val amountRub: Int,
    @SerialName("confirmation_url")
    val confirmationUrl: String,
    @SerialName("idempotence_key")
    val idempotenceKey: String,
    @SerialName("tax_mode")
    val taxMode: String? = null,
    @SerialName("fiscalization_status")
    val fiscalizationStatus: String? = null,
    val status: String
)

@Serializable
data class PaymentStatusResponse(
    @SerialName("payment_id")
    val paymentId: String,
    @SerialName("amount_rub")
    val amountRub: Int,
    @SerialName("confirmation_url")
    val confirmationUrl: String,
    @SerialName("provider_payment_id")
    val providerPaymentId: String? = null,
    val status: String,
    @SerialName("tax_mode")
    val taxMode: String? = null,
    @SerialName("fiscalization_status")
    val fiscalizationStatus: String? = null,
    @SerialName("debt_rub")
    val debtRub: Int? = null
)
