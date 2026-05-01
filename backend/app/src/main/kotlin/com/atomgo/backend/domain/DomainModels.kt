package com.atomgo.backend.domain

import java.time.Instant
import java.time.LocalDate

enum class Role { ADMIN, CLIENT }

enum class PaymentType {
    DAY,
    WEEK,
    TWO_WEEKS,
    MONTH,
    DEBT_EXACT;

    companion object {
        fun fromApi(value: String): PaymentType? = when (value) {
            "day" -> DAY
            "week" -> WEEK
            "two_weeks" -> TWO_WEEKS
            "month" -> MONTH
            "debt_exact" -> DEBT_EXACT
            else -> null
        }

        fun toApi(value: PaymentType): String = when (value) {
            DAY -> "day"
            WEEK -> "week"
            TWO_WEEKS -> "two_weeks"
            MONTH -> "month"
            DEBT_EXACT -> "debt_exact"
        }
    }
}

enum class LedgerType { CHARGE, PAYMENT, ADJUSTMENT }

data class UserSession(
    val userId: String,
    val role: Role,
    val clientId: String?
)

data class AppUser(
    val id: String,
    val login: String,
    val password: String,
    val role: Role,
    val clientId: String?
)

data class ClientAccount(
    val id: String,
    val fullName: String,
    val weeklyRateRub: Int,
    val rentalStartDate: LocalDate,
    val bikeModel: String,
    val bikeAvatarUrl: String,
    val address: String,
    val passportData: String,
    val phones: MutableList<ClientPhone> = mutableListOf(),
    var totalAdjustmentRub: Int = 0
)

data class ClientPhone(
    val label: String,
    val number: String
)

data class RentalRecord(
    val id: String,
    val clientId: String,
    val bikeAvatarUrl: String,
    val bikeModel: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    var videoUrl: String?,
    var contractUrl: String?,
    var comment: String?
)

data class LedgerEntry(
    val id: String,
    val clientId: String,
    val type: LedgerType,
    val direction: Int,
    val amountRub: Int,
    val createdAt: Instant,
    val note: String? = null,
    val sourceId: String? = null
)

enum class PaymentStatus {
    NEW,
    SUCCEEDED,
    CANCELED
}

data class PaymentRecord(
    val id: String,
    val clientId: String,
    val paymentType: PaymentType,
    val amountRub: Int,
    val confirmationUrl: String,
    val idempotenceKey: String,
    var status: PaymentStatus,
    var providerPaymentId: String? = null
)
