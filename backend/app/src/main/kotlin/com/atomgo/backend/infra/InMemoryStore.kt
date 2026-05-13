package com.atomgo.backend.infra

import com.atomgo.backend.domain.AppUser
import com.atomgo.backend.domain.AdminTaxMode
import com.atomgo.backend.domain.BikeAccount
import com.atomgo.backend.domain.ClientRentalRecord
import com.atomgo.backend.domain.ClientAccount
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.PaymentRecord
import com.atomgo.backend.domain.RentalRecord
import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.UserSession
import java.time.Instant
import java.time.LocalDate

class InMemoryStore(
    val users: MutableList<AppUser>,
    val clients: MutableList<ClientAccount>,
    val bikes: MutableList<BikeAccount>,
    val rentals: MutableList<RentalRecord>,
    val clientRentals: MutableList<ClientRentalRecord>,
    val ledger: MutableList<LedgerEntry>,
    val payments: MutableList<PaymentRecord>,
    val sessions: MutableMap<String, UserSession>,
    val processedWebhookEvents: MutableSet<String>
) {
    companion object {
        fun seed(): InMemoryStore {
            val clientId = "client-001"
            val clientId2 = "client-002"
            val bikeId1 = "bike-001"
            val bikeId2 = "bike-002"
            val adminId = "admin-001"
            val adminIpId = "admin-ip-001"
            val clientUserId = "user-client-001"
            val clientUserId2 = "user-client-002"

            val users = mutableListOf(
                AppUser(
                    id = adminId,
                    login = "admin",
                    password = "admin123",
                    role = Role.ADMIN,
                    clientId = null,
                    taxMode = AdminTaxMode.SELF_EMPLOYED
                ),
                AppUser(
                    id = adminIpId,
                    login = "admin_ip",
                    password = "adminip123",
                    role = Role.ADMIN,
                    clientId = null,
                    taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR
                ),
                AppUser(
                    id = clientUserId,
                    login = "client1",
                    password = "client123",
                    role = Role.CLIENT,
                    clientId = clientId
                ),
                AppUser(
                    id = clientUserId2,
                    login = "client2",
                    password = "client234",
                    role = Role.CLIENT,
                    clientId = clientId2
                )
            )

            val clients = mutableListOf(
                ClientAccount(
                    id = clientId,
                    fullName = "Иван Петров",
                    address = "Москва, ул. Тверская, 12",
                    passportData = "45 11 234567, выдан 12.05.2019",
                    phones = mutableListOf(
                        com.atomgo.backend.domain.ClientPhone(label = "Рабочий (TG)", number = "+7 900 123-45-67"),
                        com.atomgo.backend.domain.ClientPhone(label = "Домашний", number = "+7 495 222-33-44")
                    ),
                    adminId = adminId
                ),
                ClientAccount(
                    id = clientId2,
                    fullName = "Алексей Смирнов",
                    address = "Санкт-Петербург, Невский пр., 41",
                    passportData = "40 22 987654, выдан 01.02.2020",
                    phones = mutableListOf(
                        com.atomgo.backend.domain.ClientPhone(label = "Рабочий", number = "+7 911 333-22-11")
                    ),
                    adminId = adminId
                )
            )

            val bikes = mutableListOf(
                BikeAccount(
                    id = bikeId1,
                    photoUrl = "https://example.com/bikes/ninebot-pro.png",
                    model = "Ninebot E-Bike Pro",
                    weeklyRateRub = 3000,
                    frameSerialNumber = "NB-FRAME-001",
                    motorSerialNumber = "NB-MOTOR-001",
                    batterySerialNumber1 = "NB-BAT-A-001",
                    batterySerialNumber2 = "NB-BAT-B-001",
                    adminId = adminId
                ),
                BikeAccount(
                    id = bikeId2,
                    photoUrl = "https://example.com/bikes/aventon-level2.png",
                    model = "Aventon Level 2",
                    weeklyRateRub = 2600,
                    frameSerialNumber = "AV-FRAME-002",
                    motorSerialNumber = "AV-MOTOR-002",
                    batterySerialNumber1 = "AV-BAT-A-002",
                    batterySerialNumber2 = null,
                    adminId = adminId
                )
            )

            val rentals = mutableListOf(
                RentalRecord(
                    id = "rental-001",
                    clientId = clientId,
                    bikeId = bikeId1,
                    clientLogin = "client1",
                    clientPassword = "client123",
                    startDate = LocalDate.now().minusDays(17),
                    endDate = null,
                    videoUrl = "https://youtube.com/watch?v=demo-acceptance-1",
                    contractUrl = "https://drive.google.com/file/d/contract-1/view",
                    comment = "Передан с новым аккумулятором",
                    adminId = adminId,
                    taxMode = AdminTaxMode.SELF_EMPLOYED
                ),
                RentalRecord(
                    id = "rental-000",
                    clientId = clientId,
                    bikeId = bikeId1,
                    clientLogin = "client1",
                    clientPassword = "client123",
                    startDate = LocalDate.now().minusDays(120),
                    endDate = LocalDate.now().minusDays(48),
                    videoUrl = "https://youtube.com/watch?v=demo-acceptance-old",
                    contractUrl = "https://drive.google.com/file/d/contract-old/view",
                    comment = "Возврат по апгрейду на новую модель",
                    adminId = adminId,
                    taxMode = AdminTaxMode.SELF_EMPLOYED
                ),
                RentalRecord(
                    id = "rental-002",
                    clientId = clientId2,
                    bikeId = bikeId2,
                    clientLogin = "client2",
                    clientPassword = "client234",
                    startDate = LocalDate.now().minusDays(4),
                    endDate = null,
                    videoUrl = "https://youtube.com/watch?v=demo-acceptance-2",
                    contractUrl = "https://drive.google.com/file/d/contract-2/view",
                    comment = null,
                    adminId = adminId,
                    taxMode = AdminTaxMode.SELF_EMPLOYED
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
                ),
                LedgerEntry(
                    id = "pay-4",
                    clientId = clientId2,
                    type = LedgerType.PAYMENT,
                    direction = -1,
                    amountRub = 2600,
                    createdAt = Instant.now().minusSeconds(4L * 24 * 3600),
                    sourceId = "manual"
                )
            )

            val clientRentals = rentals
                .filter { it.clientId.isNotBlank() }
                .map { rental ->
                    ClientRentalRecord(
                        id = "client-rental-${rental.id}",
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
                }
                .toMutableList()

            return InMemoryStore(
                users = users,
                clients = clients,
                bikes = bikes,
                rentals = rentals,
                clientRentals = clientRentals,
                ledger = ledger,
                payments = mutableListOf(),
                sessions = mutableMapOf(),
                processedWebhookEvents = mutableSetOf()
            )
        }
    }
}
