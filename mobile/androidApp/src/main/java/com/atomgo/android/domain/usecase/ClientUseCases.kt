package com.atomgo.android.domain.usecase

import com.atomgo.android.domain.repository.ClientRepository
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse

class ClientUseCases(
    private val clientRepository: ClientRepository
) {
    suspend fun fetchDashboard(accessToken: String): ClientDashboardResponse {
        return clientRepository.fetchDashboard(accessToken = accessToken)
    }

    suspend fun createPayment(
        accessToken: String,
        paymentType: String,
        receiptEmail: String?
    ): CreatePaymentResponse {
        return clientRepository.createPayment(
            accessToken = accessToken,
            paymentType = paymentType,
            receiptEmail = receiptEmail
        )
    }

    suspend fun fetchPaymentStatus(accessToken: String, paymentId: String): PaymentStatusResponse {
        return clientRepository.fetchPaymentStatus(accessToken = accessToken, paymentId = paymentId)
    }
}
