package com.atomgo.backend.infra

import com.atomgo.backend.domain.ClientRentalRecord
import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.RentalPipelineStatus
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

        val lifecycleStatus = store.rentals.firstOrNull { it.id == clientRental.rentalId }?.pipelineStatus
        val debt = if (clientRental.isActiveAt(today) && lifecycleStatus != RentalPipelineStatus.SOON_RETURN) {
            LedgerCalculator.debtRub(
                clientId = clientRental.clientId,
                rentalStartDate = clientRental.startDate,
                weeklyRateRub = bike.weeklyRateRub,
                entries = store.ledger,
                asOf = today,
                rentalId = clientRental.id,
                paymentDay = normalizedPaymentDay(clientRental.paymentDay, clientRental.startDate)
            )
        } else if (clientRental.endDate != null || lifecycleStatus == RentalPipelineStatus.SOON_RETURN) {
            LedgerCalculator.finalDebtOnClosure(
                clientId = clientRental.clientId,
                rentalStartDate = clientRental.startDate,
                rentalEndDate = clientRental.endDate ?: today,
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

private fun normalizedPaymentDay(paymentDay: Int, startDate: LocalDate): Int {
    return paymentDay.takeIf { it in 1..7 } ?: startDate.dayOfWeek.value
}

private fun ClientRentalRecord.isActiveAt(asOf: LocalDate): Boolean {
    return startDate <= asOf && (endDate == null || endDate.isAfter(asOf))
}
