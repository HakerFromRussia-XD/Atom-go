package com.atomgo.android.domain.usecase

import com.atomgo.android.domain.repository.AdminRepository
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminClientPhone
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminRentalDetailsResponse

class AdminUseCases(
    private val adminRepository: AdminRepository
) {
    suspend fun fetchRents(accessToken: String): List<AdminClientSummaryResponse> {
        return adminRepository.fetchRents(accessToken = accessToken)
    }

    suspend fun fetchClients(accessToken: String): List<AdminClientSummaryResponse> {
        return adminRepository.fetchClients(accessToken = accessToken)
    }

    suspend fun fetchBikes(accessToken: String): List<AdminBikeResponse> {
        return adminRepository.fetchBikes(accessToken = accessToken)
    }

    suspend fun createClient(
        accessToken: String,
        fullName: String,
        address: String,
        passportData: String,
        phoneLabel: String,
        phoneNumber: String
    ) {
        adminRepository.createClient(
            accessToken = accessToken,
            fullName = fullName,
            address = address,
            passportData = passportData,
            phoneLabel = phoneLabel,
            phoneNumber = phoneNumber
        )
    }

    suspend fun createBike(
        accessToken: String,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?
    ) {
        adminRepository.createBike(
            accessToken = accessToken,
            bikeModel = bikeModel,
            weeklyRateRub = weeklyRateRub,
            frameSerialNumber = frameSerialNumber,
            motorSerialNumber = motorSerialNumber,
            batterySerialNumber1 = batterySerialNumber1,
            batterySerialNumber2 = batterySerialNumber2
        )
    }

    suspend fun createRental(
        accessToken: String,
        clientId: String?,
        bikeId: String,
        login: String,
        password: String,
        periodStart: String,
        periodEnd: String? = null,
        videoUrl: String? = null,
        contractUrl: String? = null,
        comment: String? = null
    ) {
        adminRepository.createRental(
            accessToken = accessToken,
            clientId = clientId,
            bikeId = bikeId,
            login = login,
            password = password,
            periodStart = periodStart,
            periodEnd = periodEnd,
            videoUrl = videoUrl,
            contractUrl = contractUrl,
            comment = comment
        )
    }

    suspend fun fetchClientDetails(accessToken: String, clientId: String): AdminClientDetailsResponse {
        return adminRepository.fetchClientDetails(accessToken = accessToken, clientId = clientId)
    }

    suspend fun fetchRentalDetails(accessToken: String, rentalId: String): AdminRentalDetailsResponse {
        return adminRepository.fetchRentalDetails(accessToken = accessToken, rentalId = rentalId)
    }

    suspend fun deleteRental(accessToken: String, rentalId: String) {
        adminRepository.deleteRental(accessToken = accessToken, rentalId = rentalId)
    }

    suspend fun deleteClient(accessToken: String, clientId: String) {
        adminRepository.deleteClient(accessToken = accessToken, clientId = clientId)
    }

    suspend fun updateClient(
        accessToken: String,
        clientId: String,
        fullName: String,
        address: String,
        passportData: String,
        phones: List<AdminClientPhone>,
        comment: String?
    ) {
        adminRepository.updateClient(
            accessToken = accessToken,
            clientId = clientId,
            fullName = fullName,
            address = address,
            passportData = passportData,
            phones = phones,
            comment = comment
        )
    }

    suspend fun updateBike(
        accessToken: String,
        bikeId: String,
        photoUrl: String?,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?
    ) {
        adminRepository.updateBike(
            accessToken = accessToken,
            bikeId = bikeId,
            photoUrl = photoUrl,
            bikeModel = bikeModel,
            weeklyRateRub = weeklyRateRub,
            frameSerialNumber = frameSerialNumber,
            motorSerialNumber = motorSerialNumber,
            batterySerialNumber1 = batterySerialNumber1,
            batterySerialNumber2 = batterySerialNumber2
        )
    }

    suspend fun updateRental(
        accessToken: String,
        rentalId: String,
        bikeId: String,
        periodStart: String,
        periodEnd: String?,
        login: String?,
        password: String?,
        videoUrl: String?,
        contractUrl: String?,
        comment: String?
    ) {
        adminRepository.updateRental(
            accessToken = accessToken,
            rentalId = rentalId,
            bikeId = bikeId,
            periodStart = periodStart,
            periodEnd = periodEnd,
            login = login,
            password = password,
            videoUrl = videoUrl,
            contractUrl = contractUrl,
            comment = comment
        )
    }

    suspend fun updateRentalPipelineStatus(
        accessToken: String,
        rentalId: String,
        pipelineStatus: String
    ) {
        adminRepository.updateRentalPipelineStatus(
            accessToken = accessToken,
            rentalId = rentalId,
            pipelineStatus = pipelineStatus
        )
    }

    suspend fun finishRentalByLifecycle(accessToken: String, rentalId: String) {
        adminRepository.finishRentalByLifecycle(accessToken = accessToken, rentalId = rentalId)
    }

    suspend fun startClientRentalInExisting(
        accessToken: String,
        rentalId: String,
        clientId: String,
        login: String,
        password: String,
        periodStart: String
    ) {
        adminRepository.startClientRentalInExisting(
            accessToken = accessToken,
            rentalId = rentalId,
            clientId = clientId,
            login = login,
            password = password,
            periodStart = periodStart
        )
    }

    suspend fun adjustClientRentalDebt(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    ) {
        adminRepository.adjustClientRentalDebt(
            accessToken = accessToken,
            clientRentalId = clientRentalId,
            amountRub = amountRub,
            sign = sign,
            comment = comment
        )
    }

    suspend fun recordClientRentalCashPayment(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        comment: String?
    ) {
        adminRepository.recordClientRentalCashPayment(
            accessToken = accessToken,
            clientRentalId = clientRentalId,
            amountRub = amountRub,
            comment = comment
        )
    }

    suspend fun adjustClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    ) {
        adminRepository.adjustClientDebt(
            accessToken = accessToken,
            clientId = clientId,
            amountRub = amountRub,
            sign = sign,
            comment = comment
        )
    }
}
