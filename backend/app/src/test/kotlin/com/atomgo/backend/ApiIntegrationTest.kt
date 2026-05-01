package com.atomgo.backend

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `client login and dashboard should work`() = testApplication {
        application { module() }

        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"client1","password":"client123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)

        val loginBody = json.parseToJsonElement(login.bodyAsText()).jsonObject
        val role = loginBody["role"]?.jsonPrimitive?.content
        val token = loginBody["access_token"]?.jsonPrimitive?.content
        assertEquals("client", role)
        assertTrue(!token.isNullOrBlank())

        val dashboard = client.get("/api/v1/client/me/dashboard") {
            bearerAuth(token!!)
        }
        assertEquals(HttpStatusCode.OK, dashboard.status)

        val dashboardBody = json.parseToJsonElement(dashboard.bodyAsText()).jsonObject
        assertEquals("client-001", dashboardBody["client_id"]?.jsonPrimitive?.content)
        val weekRub = dashboardBody["presets"]?.jsonObject?.get("week_rub")?.jsonPrimitive?.content?.toInt()
        assertTrue((weekRub ?: 0) > 0)
    }

    @Test
    fun `payment and webhook should be idempotent`() = testApplication {
        application { module() }

        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"client1","password":"client123"}""")
        }

        val token = json.parseToJsonElement(login.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No token")

        val createPayment = client.post("/api/v1/payments/create") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"payment_type":"day"}""")
        }
        assertEquals(HttpStatusCode.OK, createPayment.status)

        val paymentId = json.parseToJsonElement(createPayment.bodyAsText())
            .jsonObject["payment_id"]
            ?.jsonPrimitive
            ?.content
            ?: error("No payment_id")

        val webhookPayload = """
            {
              "type":"notification",
              "event":"payment.succeeded",
              "object":{
                "id":"provider-payment-1",
                "status":"succeeded",
                "metadata":{"local_payment_id":"$paymentId"}
              }
            }
        """.trimIndent()

        val firstWebhook = client.post("/api/v1/payments/yookassa/webhook") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload)
        }
        assertEquals(HttpStatusCode.OK, firstWebhook.status)
        val firstApplied = json.parseToJsonElement(firstWebhook.bodyAsText())
            .jsonObject["applied"]
            ?.jsonPrimitive
            ?.content
            ?.toBoolean()
        assertTrue(firstApplied == true)

        val secondWebhook = client.post("/api/v1/payments/yookassa/webhook") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload)
        }
        assertEquals(HttpStatusCode.OK, secondWebhook.status)
        val secondApplied = json.parseToJsonElement(secondWebhook.bodyAsText())
            .jsonObject["applied"]
            ?.jsonPrimitive
            ?.content
            ?.toBoolean()
        assertTrue(secondApplied == false)
    }

    @Test
    fun `admin should update rental links`() = testApplication {
        application { module() }

        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"admin","password":"admin123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)

        val token = json.parseToJsonElement(login.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No token")

        val updateLinks = client.post("/api/v1/admin/rentals/rental-001/links") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "video_url":"https://youtube.com/watch?v=updated-demo",
                  "contract_url":"https://drive.google.com/file/d/updated-contract/view"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, updateLinks.status)

        val updateBody = json.parseToJsonElement(updateLinks.bodyAsText()).jsonObject
        assertEquals(
            "https://youtube.com/watch?v=updated-demo",
            updateBody["video_url"]?.jsonPrimitive?.content
        )
        assertEquals(
            "https://drive.google.com/file/d/updated-contract/view",
            updateBody["contract_url"]?.jsonPrimitive?.content
        )
    }
}
