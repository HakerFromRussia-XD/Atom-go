package com.atomgo.backend.infra

import com.atomgo.backend.domain.AdminTaxMode
import com.atomgo.backend.domain.PaymentType
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YooKassaPaymentProviderTest {

    @Test
    fun `self employed payments use default shop credentials and omit receipt`() {
        withCaptureServer { server ->
            val provider = providerFor(server)

            val payment = provider.createPayment(
                request = baseRequest(receipt = null),
                taxMode = AdminTaxMode.SELF_EMPLOYED
            )

            assertEquals("pending", payment.receiptRegistration)
            assertEquals("Basic ${basicAuth("default-shop", "default-secret")}", server.lastAuthorization)
            assertEquals("/payments", server.lastPath)
            assertTrue(!server.lastBody.contains("\"receipt\""))
        }
    }

    @Test
    fun `individual entrepreneur payments use IP shop credentials and send receipt`() {
        withCaptureServer { server ->
            val provider = providerFor(server)

            val payment = provider.createPayment(
                request = baseRequest(
                    receipt = ProviderReceipt(
                        customerEmail = "client@example.com",
                        taxSystemCode = 2,
                        vatCode = 1,
                        paymentMode = "full_payment",
                        paymentSubject = "service"
                    )
                ),
                taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR
            )

            assertEquals("pending", payment.receiptRegistration)
            assertEquals("Basic ${basicAuth("ip-shop", "ip-secret")}", server.lastAuthorization)
            assertEquals("/payments", server.lastPath)
            val payload = Json.parseToJsonElement(server.lastBody).jsonObject
            assertNotNull(payload["receipt"])
            assertTrue(server.lastBody.contains("client@example.com"))
        }
    }

    private fun providerFor(server: CaptureServer): YooKassaPaymentProvider {
        return YooKassaPaymentProvider(
            defaultConfig = YooKassaConfig(
                shopId = "default-shop",
                secretKey = "default-secret",
                apiBaseUrl = server.baseUrl,
                publicBaseUrl = "https://atom-od.ru"
            ),
            ipConfig = YooKassaConfig(
                shopId = "ip-shop",
                secretKey = "ip-secret",
                apiBaseUrl = server.baseUrl,
                publicBaseUrl = "https://atom-od.ru"
            ),
            json = Json { ignoreUnknownKeys = true }
        )
    }

    private fun baseRequest(receipt: ProviderReceipt?): ProviderCreatePaymentRequest {
        return ProviderCreatePaymentRequest(
            localPaymentId = "payment-local-001",
            clientId = "client-001",
            rentalId = "rental-001",
            paymentType = PaymentType.DAY,
            amountRub = 500,
            idempotenceKey = "idem-001",
            description = "Atom Go test payment",
            receipt = receipt
        )
    }

    private fun basicAuth(shopId: String, secretKey: String): String {
        return Base64.getEncoder()
            .encodeToString("$shopId:$secretKey".toByteArray(StandardCharsets.UTF_8))
    }

    private fun withCaptureServer(block: (CaptureServer) -> Unit) {
        val server = CaptureServer()
        try {
            server.start()
            block(server)
        } finally {
            server.stop()
        }
    }

    private class CaptureServer {
        private val server = HttpServer.create(InetSocketAddress(0), 0)
        var lastAuthorization: String? = null
            private set
        var lastPath: String? = null
            private set
        var lastBody: String = ""
            private set

        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}"

        fun start() {
            server.createContext("/payments") { exchange ->
                lastAuthorization = exchange.requestHeaders.getFirst("Authorization")
                lastPath = exchange.requestURI.path
                lastBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val response = """
                    {
                      "id": "provider-payment-001",
                      "status": "pending",
                      "amount": {"value": "500.00", "currency": "RUB"},
                      "confirmation": {
                        "type": "redirect",
                        "confirmation_url": "https://example.test/pay/provider-payment-001"
                      },
                      "metadata": {
                        "local_payment_id": "payment-local-001",
                        "client_id": "client-001",
                        "rental_id": "rental-001",
                        "payment_type": "day"
                      },
                      "receipt_registration": "pending"
                    }
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
        }

        fun stop() {
            server.stop(0)
        }
    }
}
