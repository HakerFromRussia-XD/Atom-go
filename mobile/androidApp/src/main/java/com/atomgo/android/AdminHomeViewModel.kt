package com.atomgo.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomgo.android.data.repository.DefaultAdminRepository
import com.atomgo.android.domain.repository.AdminRepository
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminClientSummaryResponse
import kotlinx.coroutines.launch

class AdminHomeViewModel(
    private val adminRepository: AdminRepository = DefaultAdminRepository()
) : ViewModel() {

    fun fetchAdminRents(accessToken: String, onResult: (Result<List<AdminClientSummaryResponse>>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                adminRepository.fetchRents(accessToken)
            }.also(onResult)
        }
    }

    fun fetchAdminClients(accessToken: String, onResult: (Result<List<AdminClientSummaryResponse>>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                adminRepository.fetchClients(accessToken)
            }.also(onResult)
        }
    }

    fun fetchAdminBikes(accessToken: String, onResult: (Result<List<AdminBikeResponse>>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                adminRepository.fetchBikes(accessToken)
            }.also(onResult)
        }
    }

    fun createAdminClient(
        accessToken: String,
        fullName: String,
        address: String,
        passportData: String,
        phoneLabel: String,
        phoneNumber: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.createClient(
                    accessToken = accessToken,
                    fullName = fullName,
                    address = address,
                    passportData = passportData,
                    phoneLabel = phoneLabel,
                    phoneNumber = phoneNumber
                )
            }.also(onResult)
        }
    }

    fun createAdminBike(
        accessToken: String,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.createBike(
                    accessToken = accessToken,
                    bikeModel = bikeModel,
                    weeklyRateRub = weeklyRateRub,
                    frameSerialNumber = frameSerialNumber,
                    motorSerialNumber = motorSerialNumber,
                    batterySerialNumber1 = batterySerialNumber1,
                    batterySerialNumber2 = batterySerialNumber2
                )
            }.also(onResult)
        }
    }

    fun createAdminRental(
        accessToken: String,
        clientId: String?,
        bikeId: String,
        login: String,
        password: String,
        periodStart: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.createRental(
                    accessToken = accessToken,
                    clientId = clientId,
                    bikeId = bikeId,
                    login = login,
                    password = password,
                    periodStart = periodStart
                )
            }.also(onResult)
        }
    }

    fun fetchAdminClientDetails(
        accessToken: String,
        clientId: String,
        onResult: (Result<AdminClientDetailsResponse>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.fetchClientDetails(accessToken = accessToken, clientId = clientId)
            }.also(onResult)
        }
    }

    fun deleteAdminRental(
        accessToken: String,
        rentalId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.deleteRental(accessToken = accessToken, rentalId = rentalId)
            }.also(onResult)
        }
    }

    fun deleteAdminClient(
        accessToken: String,
        clientId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.deleteClient(accessToken = accessToken, clientId = clientId)
            }.also(onResult)
        }
    }

    fun updateAdminClient(
        accessToken: String,
        clientId: String,
        fullName: String,
        address: String,
        passportData: String,
        phoneLabel: String,
        phoneNumber: String,
        comment: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.updateClient(
                    accessToken = accessToken,
                    clientId = clientId,
                    fullName = fullName,
                    address = address,
                    passportData = passportData,
                    phoneLabel = phoneLabel,
                    phoneNumber = phoneNumber,
                    comment = comment
                )
            }.also(onResult)
        }
    }

    fun updateAdminBike(
        accessToken: String,
        bikeId: String,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.updateBike(
                    accessToken = accessToken,
                    bikeId = bikeId,
                    bikeModel = bikeModel,
                    weeklyRateRub = weeklyRateRub,
                    frameSerialNumber = frameSerialNumber,
                    motorSerialNumber = motorSerialNumber,
                    batterySerialNumber1 = batterySerialNumber1,
                    batterySerialNumber2 = batterySerialNumber2
                )
            }.also(onResult)
        }
    }

    fun updateAdminRental(
        accessToken: String,
        rentalId: String,
        bikeId: String,
        periodStart: String,
        periodEnd: String?,
        login: String?,
        password: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.updateRental(
                    accessToken = accessToken,
                    rentalId = rentalId,
                    bikeId = bikeId,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    login = login,
                    password = password
                )
            }.also(onResult)
        }
    }

    fun finishAdminRental(
        accessToken: String,
        rentalId: String,
        bikeId: String,
        periodStart: String,
        finishDate: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        updateAdminRental(
            accessToken = accessToken,
            rentalId = rentalId,
            bikeId = bikeId,
            periodStart = periodStart,
            periodEnd = finishDate,
            login = null,
            password = null,
            onResult = onResult
        )
    }

    fun startAdminRental(
        accessToken: String,
        clientId: String,
        bikeId: String,
        login: String,
        password: String,
        periodStart: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        createAdminRental(
            accessToken = accessToken,
            clientId = clientId,
            bikeId = bikeId,
            login = login,
            password = password,
            periodStart = periodStart,
            onResult = onResult
        )
    }

    fun updateAdminRentalPipelineStatus(
        accessToken: String,
        rentalId: String,
        pipelineStatus: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.updateRentalPipelineStatus(
                    accessToken = accessToken,
                    rentalId = rentalId,
                    pipelineStatus = pipelineStatus
                )
            }.also(onResult)
        }
    }

    fun finishAdminRentalByLifecycle(
        accessToken: String,
        rentalId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.finishRentalByLifecycle(accessToken = accessToken, rentalId = rentalId)
            }.also(onResult)
        }
    }

    fun adjustAdminClientRentalDebt(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        sign: String,
        comment: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.adjustClientRentalDebt(
                    accessToken = accessToken,
                    clientRentalId = clientRentalId,
                    amountRub = amountRub,
                    sign = sign,
                    comment = comment
                )
            }.also(onResult)
        }
    }

    fun adjustAdminClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: String,
        comment: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                adminRepository.adjustClientDebt(
                    accessToken = accessToken,
                    clientId = clientId,
                    amountRub = amountRub,
                    sign = sign,
                    comment = comment
                )
            }.also(onResult)
        }
    }
}
