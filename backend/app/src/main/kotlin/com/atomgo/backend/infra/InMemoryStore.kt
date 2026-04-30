package com.atomgo.backend.infra

import com.atomgo.backend.domain.AppUser
import com.atomgo.backend.domain.ClientAccount
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.PaymentRecord
import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.UserSession
import java.time.Instant
import java.time.LocalDate

class InMemoryStore(
    val users: MutableList<AppUser>,
    val clients: MutableList<ClientAccount>,
    val ledger: MutableList<LedgerEntry>,
    val payments: MutableList<PaymentRecord>,
    val sessions: MutableMap<String, UserSession>,
    val processedWebhookEvents: MutableSet<String>
) {
    companion object {
        fun seed(): InMemoryStore {
            val clientId = "client-001"
            val adminId = "admin-001"
            val clientUserId = "user-client-001"

            val users = mutableListOf(
                AppUser(
                    id = adminId,
                    login = "admin",
                    password = "admin123",
                    role = Role.ADMIN,
                    clientId = null
                ),
                AppUser(
                    id = clientUserId,
                    login = "client1",
                    password = "client123",
                    role = Role.CLIENT,
                    clientId = clientId
                )
            )

            val clients = mutableListOf(
                ClientAccount(
                    id = clientId,
                    fullName = "Иван Петров",
                    weeklyRateRub = 3000,
                    rentalStartDate = LocalDate.now().minusDays(17),
                    bikeModel = "Ninebot E-Bike Pro",
                    bikeAvatarUrl = "https://example.com/bikes/ninebot-pro.png",
                    totalAdjustmentRub = -1500
                )
            )

            val ledger = mutableListOf(
                LedgerEntry(
                    id = "pay-1",
                    clientId = clientId,
                    type = LedgerType.PAYMENT,
                    direction = -1,
                    amountRub = 3000,
                    createdAt = Instant.now().minusSeconds(17L * 24 * 3600),
                    sourceId = "manual"
                ),
                LedgerEntry(
                    id = "pay-2",
                    clientId = clientId,
                    type = LedgerType.PAYMENT,
                    direction = -1,
                    amountRub = 3000,
                    createdAt = Instant.now().minusSeconds(9L * 24 * 3600),
                    sourceId = "manual"
                ),
                LedgerEntry(
                    id = "pay-3",
                    clientId = clientId,
                    type = LedgerType.PAYMENT,
                    direction = -1,
                    amountRub = 3000,
                    createdAt = Instant.now().minusSeconds(3L * 24 * 3600),
                    sourceId = "manual"
                ),
                LedgerEntry(
                    id = "adj-1",
                    clientId = clientId,
                    type = LedgerType.ADJUSTMENT,
                    direction = -1,
                    amountRub = 1000,
                    createdAt = Instant.now().minusSeconds(1L * 24 * 3600),
                    note = "Возврат клиенту"
                ),
                LedgerEntry(
                    id = "adj-2",
                    clientId = clientId,
                    type = LedgerType.ADJUSTMENT,
                    direction = -1,
                    amountRub = 500,
                    createdAt = Instant.now().minusSeconds(12L * 3600),
                    note = "Скидка"
                )
            )

            return InMemoryStore(
                users = users,
                clients = clients,
                ledger = ledger,
                payments = mutableListOf(),
                sessions = mutableMapOf(),
                processedWebhookEvents = mutableSetOf()
            )
        }
    }
}
