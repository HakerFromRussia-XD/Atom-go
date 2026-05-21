package com.atomgo.android.data.repository

import com.atomgo.android.BackendConfig
import com.atomgo.android.domain.repository.ClientRepository
import com.atomgo.shared.api.AtomGoApiClient
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse
import com.atomgo.shared.api.UpdateClientReceiptEmailResponse

class DefaultClientRepository : ClientRepository {
    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    override suspend fun fetchDashboard(accessToken: String): ClientDashboardResponse {
        return apiClient.fetchClientDashboard(accessToken)
    }

    override suspend fun createPayment(
        accessToken: String,
        paymentType: String,
        receiptEmail: String?
    ): CreatePaymentResponse {
        if (!receiptEmail.isNullOrBlank()) {
            apiClient.updateClientReceiptEmail(accessToken = accessToken, email = receiptEmail)
        }
        return apiClient.createPayment(accessToken = accessToken, paymentType = paymentType)
    }

    override suspend fun updateReceiptEmail(accessToken: String, email: String): UpdateClientReceiptEmailResponse {
        return apiClient.updateClientReceiptEmail(accessToken = accessToken, email = email)
    }

    override suspend fun fetchPaymentStatus(accessToken: String, paymentId: String): PaymentStatusResponse {
        return apiClient.fetchPaymentStatus(accessToken = accessToken, paymentId = paymentId)
    }
}
