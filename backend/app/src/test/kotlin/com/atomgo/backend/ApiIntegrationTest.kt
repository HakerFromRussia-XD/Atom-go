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
import kotlinx.serialization.json.jsonArray
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
    fun `admin should create client profile only`() = testApplication {
        application { module() }

        val adminToken = loginAsAdmin()

        val createClient = client.post("/api/v1/admin/clients") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "full_name":"Roman Sergeev",
                  "address":"Moscow, 123",
                  "passport_data":"1234 567890",
                  "phones":[
                    {"label":"Рабочий (TG)","number":"89859325907"},
                    {"label":" ","number":" "}
                  ]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createClient.status)

        val created = json.parseToJsonElement(createClient.bodyAsText()).jsonObject
        assertEquals("Roman Sergeev", created["full_name"]?.jsonPrimitive?.content)
        assertEquals("Moscow, 123", created["address"]?.jsonPrimitive?.content)
        assertEquals("1234 567890", created["passport_data"]?.jsonPrimitive?.content)
        val phones = created["phones"]?.jsonArray ?: error("No phones array")
        assertEquals(1, phones.size)
    }

    @Test
    fun `admin should create bike`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val createBike = client.post("/api/v1/admin/bikes") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "photo_url":"https://example.com/bikes/monster.png",
                  "bike_model":"Монстер",
                  "weekly_rate_rub":3200,
                  "frame_serial_number":"FRM-3200",
                  "motor_serial_number":"MTR-3200",
                  "battery_serial_number_1":"BAT1-3200",
                  "battery_serial_number_2":"BAT2-3200"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createBike.status)

        val bikeBody = json.parseToJsonElement(createBike.bodyAsText()).jsonObject
        assertEquals("Монстер", bikeBody["bike_model"]?.jsonPrimitive?.content)
        assertEquals(3200, bikeBody["weekly_rate_rub"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `admin should create rental and assign client credentials`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val createdClientId = createClientAndGetId(adminToken)
        val createdBikeId = createBikeAndGetId(adminToken)

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$createdClientId",
                  "bike_id":"$createdBikeId",
                  "login":"roman.client",
                  "password":"client123",
                  "period_start":"2026-05-05",
                  "period_end":"2026-05-20",
                  "video_url":"https://youtube.com/watch?v=rent-1",
                  "contract_url":"https://drive.google.com/file/d/rent-1/view",
                  "comment":"Тест аренды"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalBody = json.parseToJsonElement(createRental.bodyAsText()).jsonObject
        val rentalId = rentalBody["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")
        assertEquals("Монстер", rentalBody["bike_model"]?.jsonPrimitive?.content)

        val loginAsCreatedClient = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"roman.client","password":"client123"}""")
        }
        assertEquals(HttpStatusCode.OK, loginAsCreatedClient.status)

        val details = client.get("/api/v1/admin/clients/$createdClientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val rentals = json.parseToJsonElement(details.bodyAsText()).jsonObject["rentals"]?.jsonArray ?: error("No rentals")
        assertTrue(rentals.any { item -> item.jsonObject["rental_id"]?.jsonPrimitive?.content == rentalId })
    }

    @Test
    fun `admin create rental should validate dates and require auth`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val invalidDates = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"client-001",
                  "bike_id":"bike-001",
                  "login":"client1",
                  "password":"client123",
                  "period_start":"2026-05-10",
                  "period_end":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidDates.status)
        assertEquals(
            "period_end must be after or equal to period_start",
            json.parseToJsonElement(invalidDates.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )

        val noAuth = client.post("/api/v1/admin/rentals") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"client-001",
                  "bike_id":"bike-001",
                  "login":"client1",
                  "password":"client123",
                  "period_start":"2026-05-10"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, noAuth.status)
    }

    @Test
    fun `admin should update rental links`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val updateLinks = client.post("/api/v1/admin/rentals/rental-001/links") {
            bearerAuth(adminToken)
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

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.loginAsAdmin(): String {
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"admin","password":"admin123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        return json.parseToJsonElement(login.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No admin token")
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createClientAndGetId(adminToken: String): String {
        val createClient = client.post("/api/v1/admin/clients") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "full_name":"Roman Sergeev",
                  "address":"Moscow, 123",
                  "passport_data":"1234 567890",
                  "phones":[{"label":"Рабочий (TG)","number":"89859325907"}]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createClient.status)
        return json.parseToJsonElement(createClient.bodyAsText())
            .jsonObject["client_id"]
            ?.jsonPrimitive
            ?.content
            ?: error("No client_id")
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createBikeAndGetId(adminToken: String): String {
        val createBike = client.post("/api/v1/admin/bikes") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "photo_url":"https://example.com/bikes/monster.png",
                  "bike_model":"Монстер",
                  "weekly_rate_rub":3200,
                  "frame_serial_number":"FRM-3200",
                  "motor_serial_number":"MTR-3200",
                  "battery_serial_number_1":"BAT1-3200",
                  "battery_serial_number_2":"BAT2-3200"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createBike.status)
        return json.parseToJsonElement(createBike.bodyAsText())
            .jsonObject["bike_id"]
            ?.jsonPrimitive
            ?.content
            ?: error("No bike_id")
    }
}
