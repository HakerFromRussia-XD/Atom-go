package com.atomgo.android.data.repository

import com.atomgo.android.BackendConfig
import com.atomgo.android.domain.repository.ClientRepository
import com.atomgo.shared.api.AtomGoApiClient
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse
import com.atomgo.shared.api.UpdateClientReceiptEmailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultClientRepository : ClientRepository {
    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    private suspend fun <T> onIo(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) { block() }
    }

    override suspend fun fetchDashboard(accessToken: String): ClientDashboardResponse {
        return onIo { apiClient.fetchClientDashboard(accessToken) }
    }

    override suspend fun createPayment(
        accessToken: String,
        paymentType: String,
        receiptEmail: String?
    ): CreatePaymentResponse {
        return onIo {
            if (!receiptEmail.isNullOrBlank()) {
                apiClient.updateClientReceiptEmail(accessToken = accessToken, email = receiptEmail)
            }
            apiClient.createPayment(accessToken = accessToken, paymentType = paymentType)
        }
    }

    override suspend fun updateReceiptEmail(accessToken: String, email: String): UpdateClientReceiptEmailResponse {
        return onIo { apiClient.updateClientReceiptEmail(accessToken = accessToken, email = email) }
    }

    override suspend fun fetchPaymentStatus(accessToken: String, paymentId: String): PaymentStatusResponse {
        return onIo { apiClient.fetchPaymentStatus(accessToken = accessToken, paymentId = paymentId) }
    }
}
