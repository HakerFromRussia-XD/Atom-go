package com.atomgo.backend.infra

import com.atomgo.backend.domain.ClientRentalRecord
import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AdminCashPaymentResult(
    val clientId: String,
    val debtRub: Int,
    val totalPaidRub: Int,
    val totalAdjustmentRub: Int
)

class AdminCashPaymentService(
    private val store: InMemoryStore
) {
    fun recordForActiveClient(
        clientId: String,
        adminId: String,
        amountRub: Int,
        comment: String?,
        today: LocalDate = LocalDate.now(),
        createdAt: Instant = Instant.now()
    ): AdminCashPaymentResult? {
        require(amountRub > 0) { "amount_rub must be positive" }

        val clientRental = store.clientRentals.firstOrNull {
            it.clientId == clientId &&
                it.adminId == adminId &&
                it.deletedAt == null &&
                it.isActiveAt(today)
        } ?: return null

        return recordForClientRental(clientRental, amountRub, comment, today, createdAt)
    }

    fun recordForClientRental(
        clientRentalId: String,
        adminId: String,
        amountRub: Int,
        comment: String?,
        today: LocalDate = LocalDate.now(),
        createdAt: Instant = Instant.now()
    ): AdminCashPaymentResult? {
        require(amountRub > 0) { "amount_rub must be positive" }

        val clientRental = store.clientRentals.firstOrNull {
            it.id == clientRentalId &&
                it.adminId == adminId &&
                it.deletedAt == null
        } ?: return null

        return recordForClientRental(clientRental, amountRub, comment, today, createdAt)
    }

    private fun recordForClientRental(
        clientRental: ClientRentalRecord,
        amountRub: Int,
        comment: String?,
        today: LocalDate,
        createdAt: Instant
    ): AdminCashPaymentResult? {
        val bike = store.bikes.firstOrNull { it.id == clientRental.bikeId } ?: return null

        store.ledger += LedgerEntry(
            id = "cash-${UUID.randomUUID().toString().take(8)}",
            clientId = clientRental.clientId,
            type = LedgerType.PAYMENT,
            direction = -1,
            amountRub = amountRub,
            createdAt = createdAt,
            note = cashNote(comment),
            rentalId = clientRental.id
        )

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

        return AdminCashPaymentResult(
            clientId = clientRental.clientId,
            debtRub = debt,
            totalPaidRub = LedgerCalculator.totalPaidRub(store.ledger, clientRental.clientId, clientRental.id),
            totalAdjustmentRub = LedgerCalculator.totalAdjustmentRub(store.ledger, clientRental.clientId, clientRental.id)
        )
    }

    private fun cashNote(comment: String?): String {
        val normalizedComment = comment?.trim()?.ifBlank { null }
        return listOfNotNull(CASH_PAYMENT_NOTE_PREFIX, normalizedComment).joinToString(": ")
    }

    companion object {
        const val CASH_PAYMENT_NOTE_PREFIX = "cash"

        fun isCashLedgerEntry(entry: LedgerEntry): Boolean {
            return entry.type == LedgerType.PAYMENT &&
                entry.note?.trim()?.lowercase()?.startsWith(CASH_PAYMENT_NOTE_PREFIX) == true
        }
    }
}

private fun ClientRentalRecord.isActiveAt(asOf: LocalDate): Boolean {
    return startDate <= asOf && (endDate == null || endDate.isAfter(asOf))
}
