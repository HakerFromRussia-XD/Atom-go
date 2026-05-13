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

enum class RentalPipelineStatus {
    LONG_TERM,
    SOON_RETURN,
    IN_STOCK;

    companion object {
        fun fromApi(value: String): RentalPipelineStatus? = when (value) {
            "long_term" -> LONG_TERM
            "soon_return" -> SOON_RETURN
            "in_stock" -> IN_STOCK
            else -> null
        }

        fun toApi(value: RentalPipelineStatus): String = when (value) {
            LONG_TERM -> "long_term"
            SOON_RETURN -> "soon_return"
            IN_STOCK -> "in_stock"
        }
    }
}

data class UserSession(
    val userId: String,
    val role: Role,
    val clientId: String?,
    val rentalId: String? = null
)

data class AppUser(
    val id: String,
    val login: String,
    val password: String,
    val role: Role,
    val clientId: String?,
    val taxMode: AdminTaxMode = AdminTaxMode.SELF_EMPLOYED
)

data class ClientAccount(
    val id: String,
    val fullName: String,
    val address: String,
    val passportData: String,
    val phones: MutableList<ClientPhone> = mutableListOf(),
    val adminId: String? = null,
    /**
     * Клиентская задолженность, перенесённая на клиента при удалении (вывод из эксплуатации)
     * lifecycle-аренды с непогашенным долгом. Сумма аккумулируется по всем таким событиям
     * и не уменьшается автоматически — управляется отдельной admin-логикой (списание/оплата).
     * См. docs/14_rental_lifecycle.md §7 и docs/02_money_and_debt_rules.md §7.
     */
    val carriedDebtRub: Int = 0
)

data class ClientPhone(
    val label: String,
    val number: String
)

data class RentalRecord(
    val id: String,
    val clientId: String,
    val bikeId: String,
    val clientLogin: String? = null,
    val clientPassword: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    var videoUrl: String?,
    var contractUrl: String?,
    var comment: String?,
    val adminId: String? = null,
    val taxMode: AdminTaxMode = AdminTaxMode.SELF_EMPLOYED,
    val pipelineStatus: RentalPipelineStatus = RentalPipelineStatus.LONG_TERM
)

data class ClientRentalRecord(
    val id: String,
    val rentalId: String,
    val clientId: String,
    val bikeId: String,
    val clientLogin: String,
    val clientPassword: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    var videoUrl: String?,
    var contractUrl: String?,
    var comment: String?,
    val adminId: String? = null,
    val taxMode: AdminTaxMode = AdminTaxMode.SELF_EMPLOYED
)

data class BikeAccount(
    val id: String,
    var photoUrl: String?,
    val model: String,
    val weeklyRateRub: Int,
    val frameSerialNumber: String,
    val motorSerialNumber: String,
    val batterySerialNumber1: String,
    val batterySerialNumber2: String?,
    val adminId: String? = null
)

data class LedgerEntry(
    val id: String,
    val clientId: String,
    val type: LedgerType,
    val direction: Int,
    val amountRub: Int,
    val createdAt: Instant,
    val note: String? = null,
    val sourceId: String? = null,
    val rentalId: String? = null
)

enum class PaymentStatus {
    NEW,
    PENDING,
    SUCCEEDED,
    CANCELED,
    FAILED
}

enum class AdminTaxMode {
    SELF_EMPLOYED,
    INDIVIDUAL_ENTREPRENEUR
}

enum class FiscalizationStatus {
    NPD_RECEIPT_PENDING,
    YOOKASSA_RECEIPT_PENDING,
    FISCALIZATION_NOT_CONFIGURED
}

data class PaymentRecord(
    val id: String,
    val clientId: String,
    val paymentType: PaymentType,
    val amountRub: Int,
    val confirmationUrl: String,
    val idempotenceKey: String,
    var status: PaymentStatus,
    var providerPaymentId: String? = null,
    val rentalId: String? = null,
    val taxMode: AdminTaxMode = AdminTaxMode.SELF_EMPLOYED,
    var fiscalizationStatus: FiscalizationStatus = FiscalizationStatus.NPD_RECEIPT_PENDING
)
