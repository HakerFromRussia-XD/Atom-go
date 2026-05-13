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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
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
                  "password":"roman-client-pwd",
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
        assertEquals(createdBikeId, rentalBody["bike_id"]?.jsonPrimitive?.content)
        assertEquals("Монстер", rentalBody["bike_model"]?.jsonPrimitive?.content)

        val loginAsCreatedClient = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"roman.client","password":"roman-client-pwd"}""")
        }
        assertEquals(HttpStatusCode.OK, loginAsCreatedClient.status)

        val details = client.get("/api/v1/admin/clients/$createdClientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val rentals = json.parseToJsonElement(details.bodyAsText()).jsonObject["rentals"]?.jsonArray ?: error("No rentals")
        assertTrue(rentals.any { item ->
            item.jsonObject["bike_id"]?.jsonPrimitive?.content == createdBikeId &&
                item.jsonObject["period_start"]?.jsonPrimitive?.content == "2026-05-05"
        })
    }

    @Test
    fun `admin should reject duplicate bike serial numbers`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val duplicateSerial = client.post("/api/v1/admin/bikes") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "photo_url":"https://example.com/bikes/dupe.png",
                  "bike_model":"Duplicate Bike",
                  "weekly_rate_rub":3100,
                  "frame_serial_number":"FRAME-DUPE-01",
                  "motor_serial_number":"NB-MOTOR-001",
                  "battery_serial_number_1":"BAT-DUPE-01",
                  "battery_serial_number_2":"BAT-DUPE-02"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Conflict, duplicateSerial.status)
        assertEquals(
            "motor_serial_number is already used",
            json.parseToJsonElement(duplicateSerial.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `admin should update bike fields`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val bikeId = createBikeAndGetId(adminToken)

        val updateBike = client.post("/api/v1/admin/bikes/$bikeId") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "photo_url":"https://example.com/bikes/monster-updated.png",
                  "bike_model":"Монстер X",
                  "weekly_rate_rub":4100,
                  "frame_serial_number":"FRM-4100",
                  "motor_serial_number":"MTR-4100",
                  "battery_serial_number_1":"BAT1-4100",
                  "battery_serial_number_2":"BAT2-4100"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, updateBike.status)
        val body = json.parseToJsonElement(updateBike.bodyAsText()).jsonObject
        assertEquals("Монстер X", body["bike_model"]?.jsonPrimitive?.content)
        assertEquals(4100, body["weekly_rate_rub"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `admin should update rental period and bike`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken)
        val firstBikeId = createBikeAndGetId(adminToken)

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$firstBikeId",
                  "login":"update.rental.client",
                  "password":"update-rental-client-pwd",
                  "period_start":"2026-05-05",
                  "period_end":"2026-05-20"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental id")

        val updateRental = client.post("/api/v1/admin/rentals/$rentalId") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "bike_id":"bike-002",
                  "period_start":"2026-05-10",
                  "period_end":"2026-05-30"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, updateRental.status)
        val updated = json.parseToJsonElement(updateRental.bodyAsText()).jsonObject
        assertEquals("bike-002", updated["bike_id"]?.jsonPrimitive?.content)
        assertEquals("2026-05-10", updated["period_start"]?.jsonPrimitive?.content)
        assertEquals("2026-05-30", updated["period_end"]?.jsonPrimitive?.content)
    }

    @Test
    fun `admin should open rental details`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken)
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "DETAILS-FRAME-1", motorSerial = "DETAILS-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"rental.details.client",
                  "password":"rental-details-client-pwd",
                  "period_start":"2026-05-05"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental id")

        val details = client.get("/api/v1/admin/rentals/$rentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val body = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals(rentalId, body["rental_id"]?.jsonPrimitive?.content)
        assertEquals(clientId, body["client_id"]?.jsonPrimitive?.content)
        assertEquals("rental.details.client", body["client_login"]?.jsonPrimitive?.content)
        assertTrue(body["journal_entries"]?.jsonArray != null)
    }

    @Test
    fun `in stock rental clears credentials while closed client rental keeps credentials`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Stored Credentials")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "DETAILS-FRAME-2", motorSerial = "DETAILS-MOTOR-2")

        val login = "stored.client.login"
        val password = "stored.client.password"

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"$login",
                  "password":"$password",
                  "period_start":"2026-05-05"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental id")

        val finish = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finish.status)

        val details = client.get("/api/v1/admin/rentals/$rentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val body = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals(false, body["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertEquals(null, body["client_login"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, body["client_password"]?.jsonPrimitive?.contentOrNull)

        val clientDetails = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, clientDetails.status)
        val closedClientRentalId = json.parseToJsonElement(clientDetails.bodyAsText())
            .jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { item -> item.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId }
            ?.jsonObject
            ?.get("rental_id")
            ?.jsonPrimitive
            ?.content
            ?: error("No closed client rental")

        val closedDetails = client.get("/api/v1/admin/rentals/$closedClientRentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, closedDetails.status)
        val closedBody = json.parseToJsonElement(closedDetails.bodyAsText()).jsonObject
        assertEquals(login, closedBody["client_login"]?.jsonPrimitive?.content)
        assertEquals(password, closedBody["client_password"]?.jsonPrimitive?.content)
        assertTrue(closedBody["completed_at"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `admin should delete rental`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken)
        val bikeId = createBikeAndGetId(adminToken)

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"delete.rental.client",
                  "password":"delete-rental-client-pwd",
                  "period_start":"2026-06-01",
                  "period_end":"2026-06-15"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental id")

        val deleteRental = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)
        val deleteBody = json.parseToJsonElement(deleteRental.bodyAsText()).jsonObject
        assertEquals(rentalId, deleteBody["rental_id"]?.jsonPrimitive?.content)
        assertEquals(true, deleteBody["deleted"]?.jsonPrimitive?.content?.toBoolean())

        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val rentals = json.parseToJsonElement(details.bodyAsText()).jsonObject["rentals"]?.jsonArray ?: error("No rentals")
        assertTrue(rentals.none { item -> item.jsonObject["rental_id"]?.jsonPrimitive?.content == rentalId })
    }

    @Test
    fun `deleting lifecycle rental should close and preserve active client rental history`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Deleted Lifecycle Client", phone = "79000009001")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "DELETE-LIFE-FRAME-1", motorSerial = "DELETE-LIFE-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"deleted.lifecycle",
                  "password":"deleted123",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val lifecycleRentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val deleteRental = client.post("/api/v1/admin/rentals/$lifecycleRentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)

        val rents = client.get("/api/v1/admin/rents") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, rents.status)
        assertTrue(
            json.parseToJsonElement(rents.bodyAsText()).jsonArray.none { item ->
                item.jsonObject["rental_id"]?.jsonPrimitive?.content == lifecycleRentalId
            }
        )

        val clientDetails = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, clientDetails.status)
        val closedClientRental = json.parseToJsonElement(clientDetails.bodyAsText())
            .jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { item -> item.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId }
            ?.jsonObject
            ?: error("Closed client rental not found after lifecycle deletion")
        val closedClientRentalId = closedClientRental["rental_id"]?.jsonPrimitive?.content ?: error("No client rental id")
        assertTrue(closedClientRentalId != lifecycleRentalId)
        assertEquals(LocalDate.now().toString(), closedClientRental["period_end"]?.jsonPrimitive?.content)

        val closedDetails = client.get("/api/v1/admin/rentals/$closedClientRentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, closedDetails.status)
        val closedDetailsJson = json.parseToJsonElement(closedDetails.bodyAsText()).jsonObject
        assertEquals(closedClientRentalId, closedDetailsJson["rental_id"]?.jsonPrimitive?.content)
        assertEquals(clientId, closedDetailsJson["client_id"]?.jsonPrimitive?.content)
        assertEquals("deleted.lifecycle", closedDetailsJson["client_login"]?.jsonPrimitive?.content)
        assertEquals("deleted123", closedDetailsJson["client_password"]?.jsonPrimitive?.content)
        assertEquals(LocalDate.now().toString(), closedDetailsJson["completed_at"]?.jsonPrimitive?.content)

        val historicalLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"deleted.lifecycle","password":"deleted123"}""")
        }
        assertEquals(HttpStatusCode.OK, historicalLogin.status)
    }

    @Test
    fun `deleting by active client rental id should delete lifecycle rental and preserve history`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Delete By Client Rental", phone = "79000009002")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "DELETE-BY-CLIENT-FRAME-1", motorSerial = "DELETE-BY-CLIENT-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"delete.by.clientrental",
                  "password":"delete123",
                  "period_start":"2026-05-02"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val lifecycleRentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val clientDetailsBeforeDelete = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, clientDetailsBeforeDelete.status)
        val activeClientRentalId = json.parseToJsonElement(clientDetailsBeforeDelete.bodyAsText())
            .jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { item -> item.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId }
            ?.jsonObject
            ?.get("rental_id")
            ?.jsonPrimitive
            ?.content
            ?: error("No active client rental id")
        assertTrue(activeClientRentalId != lifecycleRentalId)

        val deleteRental = client.post("/api/v1/admin/rentals/$activeClientRentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)

        val rents = client.get("/api/v1/admin/rents") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, rents.status)
        val rentsJson = json.parseToJsonElement(rents.bodyAsText()).jsonArray
        assertTrue(rentsJson.none { item -> item.jsonObject["rental_id"]?.jsonPrimitive?.content == lifecycleRentalId })

        val clientDetailsAfterDelete = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, clientDetailsAfterDelete.status)
        val historicalRental = json.parseToJsonElement(clientDetailsAfterDelete.bodyAsText())
            .jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { item -> item.jsonObject["rental_id"]?.jsonPrimitive?.content == activeClientRentalId }
            ?.jsonObject
            ?: error("Historical rental not found after delete by client-rental id")
        assertEquals(LocalDate.now().toString(), historicalRental["period_end"]?.jsonPrimitive?.content)

        val historicalLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"delete.by.clientrental","password":"delete123"}""")
        }
        assertEquals(HttpStatusCode.OK, historicalLogin.status)
    }

    @Test
    fun `finish rental should invalidate old session but keep historical client-rental login`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Detach Client", phone = "79005550001")
        val bikeId = createBikeAndGetId(
            adminToken = adminToken,
            frameSerial = "DETACH-FRAME-1",
            motorSerial = "DETACH-MOTOR-1"
        )

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"detach.client",
                  "password":"detach-client-pwd",
                  "period_start":"2026-05-05"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental id")

        val clientLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"detach.client","password":"detach-client-pwd"}""")
        }
        assertEquals(HttpStatusCode.OK, clientLogin.status)
        val clientToken = json.parseToJsonElement(clientLogin.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No client token")

        val finish = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finish.status)
        val finishedAt = LocalDate.now().toString()

        val rentsAfterFinish = client.get("/api/v1/admin/rents") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, rentsAfterFinish.status)
        val rentEntry = json.parseToJsonElement(rentsAfterFinish.bodyAsText())
            .jsonArray
            .firstOrNull { it.jsonObject["rental_id"]?.jsonPrimitive?.content == rentalId }
            ?.jsonObject
            ?: error("Rental card not found after finish")
        assertEquals(false, rentEntry["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())

        val dashboardWithOldToken = client.get("/api/v1/client/me/dashboard") {
            bearerAuth(clientToken)
        }
        assertEquals(HttpStatusCode.Unauthorized, dashboardWithOldToken.status)

        val loginAfterDetach = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"detach.client","password":"detach-client-pwd"}""")
        }
        assertEquals(HttpStatusCode.OK, loginAfterDetach.status)

        val historicalToken = json.parseToJsonElement(loginAfterDetach.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No historical token")

        val historicalDashboard = client.get("/api/v1/client/me/dashboard") {
            bearerAuth(historicalToken)
        }
        assertEquals(HttpStatusCode.OK, historicalDashboard.status)
        val historicalDashboardBody = json.parseToJsonElement(historicalDashboard.bodyAsText()).jsonObject
        assertEquals(clientId, historicalDashboardBody["client_id"]?.jsonPrimitive?.content)
        assertEquals(false, historicalDashboardBody["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertEquals(finishedAt, historicalDashboardBody["completed_at"]?.jsonPrimitive?.content)
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
                  "password":"client1-pwd",
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
                  "password":"client1-pwd",
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

    @Test
    fun `admin should update client profile with phones`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken)

        val updateClient = client.post("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "full_name":"Roman Sergeev Updated",
                  "address":"Moscow, Tverskaya 12",
                  "passport_data":"9876 543210",
                  "phones":[
                    {"label":"Рабочий (TG)","number":"89859325907"},
                    {"label":"Домашний","number":"84952223344"}
                  ]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, updateClient.status)
        val updateBody = json.parseToJsonElement(updateClient.bodyAsText()).jsonObject
        assertEquals("Roman Sergeev Updated", updateBody["full_name"]?.jsonPrimitive?.content)
        assertEquals("Moscow, Tverskaya 12", updateBody["address"]?.jsonPrimitive?.content)
        assertEquals("9876 543210", updateBody["passport_data"]?.jsonPrimitive?.content)
        assertEquals(2, updateBody["phones"]?.jsonArray?.size)

        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val detailsBody = json.parseToJsonElement(details.bodyAsText()).jsonObject
        val phones = detailsBody["phones"]?.jsonArray ?: error("No phones")
        assertEquals(2, phones.size)
        assertTrue(phones.any { it.jsonObject["label"]?.jsonPrimitive?.content == "Домашний" })
    }

    @Test
    fun `admin should create rental from client details endpoint`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken)
        val bikeId = createBikeAndGetId(adminToken)

        val createRental = client.post("/api/v1/admin/clients/$clientId/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "bike_id":"$bikeId",
                  "login":"client.from.details",
                  "password":"client-from-details-pwd",
                  "period_start":"2026-05-15",
                  "period_end":"2026-05-30",
                  "video_url":"https://youtube.com/watch?v=from-details",
                  "contract_url":"https://drive.google.com/file/d/from-details/view",
                  "comment":"Created from details endpoint"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalBody = json.parseToJsonElement(createRental.bodyAsText()).jsonObject
        assertEquals(bikeId, rentalBody["bike_id"]?.jsonPrimitive?.content)
        assertEquals("Created from details endpoint", rentalBody["comment"]?.jsonPrimitive?.content)

        val loginAsCreatedClient = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"client.from.details","password":"client-from-details-pwd"}""")
        }
        assertEquals(HttpStatusCode.OK, loginAsCreatedClient.status)
    }

    @Test
    fun `admin should reject duplicate serials inside bike payload`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val createBike = client.post("/api/v1/admin/bikes") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "photo_url":"https://example.com/bikes/invalid.png",
                  "bike_model":"Invalid Bike",
                  "weekly_rate_rub":3000,
                  "frame_serial_number":"SER-100",
                  "motor_serial_number":"SER-100",
                  "battery_serial_number_1":"BAT-100",
                  "battery_serial_number_2":"BAT-200"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Conflict, createBike.status)
        assertEquals(
            "serial numbers must be unique inside bike",
            json.parseToJsonElement(createBike.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `admin accounts should see only their own clients rentals and tax pipelines`() = testApplication {
        application { module() }
        val selfEmployedAdminToken = loginAsAdmin()
        val ipAdminToken = loginAsAdminIp()

        val ipInitialClients = client.get("/api/v1/admin/clients") {
            bearerAuth(ipAdminToken)
        }
        assertEquals(HttpStatusCode.OK, ipInitialClients.status)
        assertEquals(0, json.parseToJsonElement(ipInitialClients.bodyAsText()).jsonArray.size)

        val ipClientId = createClientAndGetId(
            ipAdminToken,
            fullName = "IP Client",
            phone = "79005551122"
        )

        val ipClientsAfterProfileCreate = client.get("/api/v1/admin/clients") {
            bearerAuth(ipAdminToken)
        }
        assertEquals(HttpStatusCode.OK, ipClientsAfterProfileCreate.status)
        assertTrue(
            json.parseToJsonElement(ipClientsAfterProfileCreate.bodyAsText()).jsonArray.any {
                it.jsonObject["client_id"]?.jsonPrimitive?.content == ipClientId
            }
        )

        val ipRentsAfterProfileCreate = client.get("/api/v1/admin/rents") {
            bearerAuth(ipAdminToken)
        }
        assertEquals(HttpStatusCode.OK, ipRentsAfterProfileCreate.status)
        assertTrue(
            json.parseToJsonElement(ipRentsAfterProfileCreate.bodyAsText()).jsonArray.none {
                it.jsonObject["client_id"]?.jsonPrimitive?.content == ipClientId
            }
        )

        val ipBikeId = createBikeAndGetId(ipAdminToken, frameSerial = "IP-FRAME-1", motorSerial = "IP-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(ipAdminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$ipClientId",
                  "bike_id":"$ipBikeId",
                  "login":"ip.client",
                  "password":"ip-client-pwd",
                  "period_start":"2026-05-05"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalBody = json.parseToJsonElement(createRental.bodyAsText()).jsonObject
        assertEquals("individual_entrepreneur", rentalBody["tax_mode"]?.jsonPrimitive?.content)

        val ipRentsAfterRentalCreate = client.get("/api/v1/admin/rents") {
            bearerAuth(ipAdminToken)
        }
        assertEquals(HttpStatusCode.OK, ipRentsAfterRentalCreate.status)
        assertTrue(
            json.parseToJsonElement(ipRentsAfterRentalCreate.bodyAsText()).jsonArray.any {
                it.jsonObject["client_id"]?.jsonPrimitive?.content == ipClientId
            }
        )

        val selfEmployedClients = client.get("/api/v1/admin/clients") {
            bearerAuth(selfEmployedAdminToken)
        }
        assertEquals(HttpStatusCode.OK, selfEmployedClients.status)
        assertTrue(
            json.parseToJsonElement(selfEmployedClients.bodyAsText()).jsonArray.none {
                it.jsonObject["client_id"]?.jsonPrimitive?.content == ipClientId
            }
        )

        val forbiddenDetails = client.get("/api/v1/admin/clients/$ipClientId") {
            bearerAuth(selfEmployedAdminToken)
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenDetails.status)

        val ipClientLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"ip.client","password":"ip-client-pwd"}""")
        }
        assertEquals(HttpStatusCode.OK, ipClientLogin.status)
        val clientToken = json.parseToJsonElement(ipClientLogin.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No client token")

        val dashboardBeforeEmail = client.get("/api/v1/client/me/dashboard") {
            bearerAuth(clientToken)
        }
        assertEquals(HttpStatusCode.OK, dashboardBeforeEmail.status)
        val dashboardBeforeEmailBody = json.parseToJsonElement(dashboardBeforeEmail.bodyAsText()).jsonObject
        assertEquals("individual_entrepreneur", dashboardBeforeEmailBody["tax_mode"]?.jsonPrimitive?.content)
        assertEquals(true, dashboardBeforeEmailBody["requires_receipt_email"]?.jsonPrimitive?.content?.toBooleanStrict())

        val paymentWithoutEmail = client.post("/api/v1/payments/create") {
            bearerAuth(clientToken)
            contentType(ContentType.Application.Json)
            setBody("""{"payment_type":"day"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, paymentWithoutEmail.status)

        val saveReceiptEmail = client.post("/api/v1/client/me/receipt-email") {
            bearerAuth(clientToken)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"13romaroma13@gmail.com"}""")
        }
        assertEquals(HttpStatusCode.OK, saveReceiptEmail.status)

        val dashboardAfterEmail = client.get("/api/v1/client/me/dashboard") {
            bearerAuth(clientToken)
        }
        assertEquals(HttpStatusCode.OK, dashboardAfterEmail.status)
        val dashboardAfterEmailBody = json.parseToJsonElement(dashboardAfterEmail.bodyAsText()).jsonObject
        assertEquals(false, dashboardAfterEmailBody["requires_receipt_email"]?.jsonPrimitive?.content?.toBooleanStrict())

        val payment = client.post("/api/v1/payments/create") {
            bearerAuth(clientToken)
            contentType(ContentType.Application.Json)
            setBody("""{"payment_type":"day"}""")
        }
        assertEquals(HttpStatusCode.OK, payment.status)
        val paymentBody = json.parseToJsonElement(payment.bodyAsText()).jsonObject
        assertEquals("individual_entrepreneur", paymentBody["tax_mode"]?.jsonPrimitive?.content)
        assertEquals("yookassa_receipt_pending", paymentBody["fiscalization_status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `admin should delete only clients and bikes without rental history`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val draftClientId = createClientAndGetId(adminToken, fullName = "Draft Client", phone = "79005559901")
        val deleteDraftClient = client.post("/api/v1/admin/clients/$draftClientId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteDraftClient.status)

        val spareBikeId = createBikeAndGetId(
            adminToken = adminToken,
            frameSerial = "DELETE-FRAME-1",
            motorSerial = "DELETE-MOTOR-1"
        )
        val deleteSpareBike = client.post("/api/v1/admin/bikes/$spareBikeId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteSpareBike.status)

        val rentedClientId = createClientAndGetId(adminToken, fullName = "Rented Client", phone = "79005559902")
        val rentedBikeId = createBikeAndGetId(
            adminToken = adminToken,
            frameSerial = "RENTED-FRAME-1",
            motorSerial = "RENTED-MOTOR-1"
        )
        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$rentedClientId",
                  "bike_id":"$rentedBikeId",
                  "login":"delete.rules.client",
                  "password":"delete-rules-client-pwd",
                  "period_start":"2026-05-06"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)

        val deleteRentedClient = client.post("/api/v1/admin/clients/$rentedClientId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.Conflict, deleteRentedClient.status)
        assertEquals(
            "client is used by rentals",
            json.parseToJsonElement(deleteRentedClient.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )

        val deleteRentedBike = client.post("/api/v1/admin/bikes/$rentedBikeId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.Conflict, deleteRentedBike.status)
        assertEquals(
            "bike is used by rentals",
            json.parseToJsonElement(deleteRentedBike.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `finishing rental should keep closed rental in previous client history`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val firstClientId = createClientAndGetId(adminToken, fullName = "Client One", phone = "79000001001")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "LIFE-FRAME-1", motorSerial = "LIFE-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$firstClientId",
                  "bike_id":"$bikeId",
                  "login":"life.client",
                  "password":"life123",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val finishRental = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finishRental.status)

        val firstClientDetails = client.get("/api/v1/admin/clients/$firstClientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, firstClientDetails.status)
        val rentals = json.parseToJsonElement(firstClientDetails.bodyAsText()).jsonObject["rentals"]?.jsonArray
            ?: error("No rentals")
        val closedRental = rentals.firstOrNull { rental ->
            rental.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId &&
                rental.jsonObject["period_start"]?.jsonPrimitive?.content == "2026-05-01"
        } ?: error("Closed rental should stay in previous client history")
        assertTrue(closedRental.jsonObject["period_end"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `starting new client rental must not delete previous client closed rental`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val firstClientId = createClientAndGetId(adminToken, fullName = "Cycle Client One", phone = "79000002001")
        val secondClientId = createClientAndGetId(adminToken, fullName = "Cycle Client Two", phone = "79000002002")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "LIFE-FRAME-2", motorSerial = "LIFE-MOTOR-2")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$firstClientId",
                  "bike_id":"$bikeId",
                  "login":"cycle.client",
                  "password":"cycle123",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val finishRental = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finishRental.status)

        val startNewClientRental = client.post("/api/v1/admin/rentals/$rentalId/client-rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$secondClientId",
                  "login":"cycle.client",
                  "password":"cycle456",
                  "period_start":"2026-05-12"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, startNewClientRental.status)

        val firstClientDetails = client.get("/api/v1/admin/clients/$firstClientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, firstClientDetails.status)
        val firstClientRentals = json.parseToJsonElement(firstClientDetails.bodyAsText()).jsonObject["rentals"]?.jsonArray
            ?: error("No rentals")
        val preservedClosedRental = firstClientRentals.firstOrNull { rental ->
            rental.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId &&
                rental.jsonObject["period_start"]?.jsonPrimitive?.content == "2026-05-01" &&
                rental.jsonObject["period_end"]?.jsonPrimitive?.content?.isNotBlank() == true
        }
        assertTrue(
            preservedClosedRental != null,
            "Previous client rental history should be preserved after assigning a new client"
        )
    }

    @Test
    fun `finished rental should be detached from client on admin rents screen`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val clientId = createClientAndGetId(adminToken, fullName = "Detached Client", phone = "79000003001")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "DETACH-FRAME-1", motorSerial = "DETACH-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"detached.client",
                  "password":"detached123",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val finishRental = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finishRental.status)

        val rents = client.get("/api/v1/admin/rents") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, rents.status)
        val rentsBody = json.parseToJsonElement(rents.bodyAsText()).jsonArray
        val finishedCard = rentsBody.firstOrNull { item ->
            item.jsonObject["rental_id"]?.jsonPrimitive?.content == rentalId
        }?.jsonObject ?: error("Finished rental card not found on /admin/rents")

        assertEquals("", finishedCard["client_id"]?.jsonPrimitive?.content)
        assertEquals("", finishedCard["full_name"]?.jsonPrimitive?.content)
        assertEquals(false, finishedCard["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertEquals(0, finishedCard["debt_rub"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `finished rental should not keep bike bound in admin clients summary`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val clientId = createClientAndGetId(adminToken, fullName = "Detached Summary Client", phone = "79000004001")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "DETACH-FRAME-2", motorSerial = "DETACH-MOTOR-2")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"detached.summary",
                  "password":"detached456",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val finishRental = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finishRental.status)

        val clientsResponse = client.get("/api/v1/admin/clients") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, clientsResponse.status)
        val clientsJson = json.parseToJsonElement(clientsResponse.bodyAsText()).jsonArray
        val targetClient = clientsJson.firstOrNull { item ->
            item.jsonObject["client_id"]?.jsonPrimitive?.content == clientId
        }?.jsonObject ?: error("Client summary not found")

        assertEquals("-", targetClient["bike_model"]?.jsonPrimitive?.content)
        assertEquals("", targetClient["bike_avatar_url"]?.jsonPrimitive?.content)
        assertEquals(false, targetClient["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())
    }

    @Test
    fun `closed client rental details should include completed_at while lifecycle in stock does not`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val clientId = createClientAndGetId(adminToken, fullName = "Closed Details Client", phone = "79000005001")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "CLOSE-FRAME-1", motorSerial = "CLOSE-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"closed.details",
                  "password":"closed123",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val finishRental = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finishRental.status)

        val details = client.get("/api/v1/admin/rentals/$rentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val detailsJson = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals("in_stock", detailsJson["rental_pipeline_status"]?.jsonPrimitive?.content)
        assertEquals(null, detailsJson["completed_at"]?.jsonPrimitive?.contentOrNull)
        assertEquals("", detailsJson["client_id"]?.jsonPrimitive?.content)
        assertEquals(null, detailsJson["client_login"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, detailsJson["client_password"]?.jsonPrimitive?.contentOrNull)
        assertEquals(0, detailsJson["journal_entries"]?.jsonArray?.size)

        val clientDetails = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, clientDetails.status)
        val closedClientRentalId = json.parseToJsonElement(clientDetails.bodyAsText())
            .jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { item -> item.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId }
            ?.jsonObject
            ?.get("rental_id")
            ?.jsonPrimitive
            ?.content
            ?: error("Closed client rental not found")

        val closedDetails = client.get("/api/v1/admin/rentals/$closedClientRentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, closedDetails.status)
        val closedDetailsJson = json.parseToJsonElement(closedDetails.bodyAsText()).jsonObject
        assertEquals(LocalDate.now().toString(), closedDetailsJson["completed_at"]?.jsonPrimitive?.content)
        assertEquals(clientId, closedDetailsJson["client_id"]?.jsonPrimitive?.content)
        assertEquals("closed.details", closedDetailsJson["client_login"]?.jsonPrimitive?.content)
        assertEquals("closed123", closedDetailsJson["client_password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `finish should clear in stock card stats renter and journal while preserving closed client rental`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val clientId = createClientAndGetId(adminToken, fullName = "InStock Lifecycle Client", phone = "79000006001")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "INSTOCK-FRAME-1", motorSerial = "INSTOCK-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"instock.client",
                  "password":"instock123",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val finishRental = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finishRental.status)

        val details = client.get("/api/v1/admin/rentals/$rentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val detailsJson = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals("", detailsJson["client_id"]?.jsonPrimitive?.content)
        assertEquals("", detailsJson["client_full_name"]?.jsonPrimitive?.content)
        assertEquals(null, detailsJson["client_login"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, detailsJson["client_password"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, detailsJson["completed_at"]?.jsonPrimitive?.contentOrNull)
        assertEquals("in_stock", detailsJson["rental_pipeline_status"]?.jsonPrimitive?.content)
        assertEquals(0, detailsJson["debt_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, detailsJson["total_paid_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, detailsJson["total_adjustment_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, detailsJson["journal_entries"]?.jsonArray?.size)

        val clientDetails = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, clientDetails.status)
        val rentals = json.parseToJsonElement(clientDetails.bodyAsText()).jsonObject["rentals"]?.jsonArray
            ?: error("No rentals in client details")
        val closedRental = rentals.firstOrNull { item ->
            item.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId &&
                item.jsonObject["period_end"]?.jsonPrimitive?.content?.isNotBlank() == true
        }
        assertTrue(closedRental != null, "Closed rental should be preserved in client history after finish")
    }

    @Test
    fun `starting next client rental should reuse lifecycle rental and keep previous credentials distinct`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val firstClientId = createClientAndGetId(adminToken, fullName = "First Credentials Client", phone = "79000007001")
        val secondClientId = createClientAndGetId(adminToken, fullName = "Second Credentials Client", phone = "79000007002")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "REUSE-FRAME-1", motorSerial = "REUSE-MOTOR-1")

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$firstClientId",
                  "bike_id":"$bikeId",
                  "login":"reuse.client",
                  "password":"reuse123",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val lifecycleRentalId = json.parseToJsonElement(createRental.bodyAsText()).jsonObject["rental_id"]?.jsonPrimitive?.content
            ?: error("No rental_id")

        val finishRental = client.post("/api/v1/admin/rentals/$lifecycleRentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finishRental.status)

        val firstClientDetails = client.get("/api/v1/admin/clients/$firstClientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, firstClientDetails.status)
        val firstClosedClientRentalId = json.parseToJsonElement(firstClientDetails.bodyAsText())
            .jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { item -> item.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId }
            ?.jsonObject
            ?.get("rental_id")
            ?.jsonPrimitive
            ?.content
            ?: error("First closed client rental not found")
        assertTrue(firstClosedClientRentalId != lifecycleRentalId)

        val startNewClientRental = client.post("/api/v1/admin/rentals/$lifecycleRentalId/client-rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$secondClientId",
                  "login":"reuse.client",
                  "password":"reuse456",
                  "period_start":"2026-05-12"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, startNewClientRental.status)
        val startBody = json.parseToJsonElement(startNewClientRental.bodyAsText()).jsonObject
        assertEquals(lifecycleRentalId, startBody["rental_id"]?.jsonPrimitive?.content)

        val rents = client.get("/api/v1/admin/rents") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, rents.status)
        val matchingLifecycleCards = json.parseToJsonElement(rents.bodyAsText()).jsonArray.count { item ->
            item.jsonObject["bike_model"]?.jsonPrimitive?.content == "Монстер"
        }
        assertTrue(matchingLifecycleCards >= 1)
        assertTrue(
            json.parseToJsonElement(rents.bodyAsText()).jsonArray.count { item ->
                item.jsonObject["rental_id"]?.jsonPrimitive?.content == lifecycleRentalId
            } == 1
        )

        val currentLifecycleDetails = client.get("/api/v1/admin/rentals/$lifecycleRentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, currentLifecycleDetails.status)
        val currentDetailsJson = json.parseToJsonElement(currentLifecycleDetails.bodyAsText()).jsonObject
        assertEquals(secondClientId, currentDetailsJson["client_id"]?.jsonPrimitive?.content)
        assertEquals("reuse.client", currentDetailsJson["client_login"]?.jsonPrimitive?.content)
        assertEquals("reuse456", currentDetailsJson["client_password"]?.jsonPrimitive?.content)
        assertEquals(true, currentDetailsJson["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())

        val oldCredentialsLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"reuse.client","password":"reuse123"}""")
        }
        assertEquals(HttpStatusCode.OK, oldCredentialsLogin.status)
        val oldToken = json.parseToJsonElement(oldCredentialsLogin.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No old token")
        val oldDashboard = client.get("/api/v1/client/me/dashboard") {
            bearerAuth(oldToken)
        }
        assertEquals(HttpStatusCode.OK, oldDashboard.status)
        val oldDashboardJson = json.parseToJsonElement(oldDashboard.bodyAsText()).jsonObject
        assertEquals(firstClientId, oldDashboardJson["client_id"]?.jsonPrimitive?.content)
        assertEquals(false, oldDashboardJson["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertEquals(LocalDate.now().toString(), oldDashboardJson["completed_at"]?.jsonPrimitive?.content)
    }

    @Test
    fun `admin cannot create second lifecycle rental for same bike`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()

        val firstClientId = createClientAndGetId(adminToken, fullName = "Duplicate Bike Client One", phone = "79000008001")
        val secondClientId = createClientAndGetId(adminToken, fullName = "Duplicate Bike Client Two", phone = "79000008002")
        val bikeId = createBikeAndGetId(adminToken, frameSerial = "DUP-LIFE-FRAME-1", motorSerial = "DUP-LIFE-MOTOR-1")

        val firstRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$firstClientId",
                  "bike_id":"$bikeId",
                  "login":"duplicate.lifecycle.one",
                  "password":"duplicate-lifecycle-one-pwd",
                  "period_start":"2026-05-01"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, firstRental.status)

        val duplicateRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$secondClientId",
                  "bike_id":"$bikeId",
                  "login":"duplicate.lifecycle.two",
                  "password":"client456",
                  "period_start":"2026-05-12"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Conflict, duplicateRental.status)
        assertEquals(
            "bike already has rental",
            json.parseToJsonElement(duplicateRental.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )
    }

    // ---------------------------------------------------------------------------
    // Удаление lifecycle-аренды: перенос долга на клиента
    // Покрывает docs/14_rental_lifecycle.md §7 и docs/02_money_and_debt_rules.md §7
    // ---------------------------------------------------------------------------

    /**
     * Аренда стартует сегодня, удаляется сегодня же — used_days=0, overdue=0,
     * финальный долг = 0, carriedDebt не меняется.
     */
    @Test
    fun `delete same-day rental should keep carriedDebt at zero`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Zero Debt Carry", phone = "79009990001")
        val bikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "CARRY-ZERO-FRAME",
            motorSerial = "CARRY-ZERO-MOTOR",
            weeklyRateRub = 3500
        )
        val start = LocalDate.now().toString()

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"carry.zero",
                  "password":"carry123",
                  "period_start":"$start"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")

        val deleteRental = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)

        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val body = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals(
            0,
            body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt(),
            "carried_debt_rub must remain 0 when used_days == 0"
        )
    }

    /**
     * Аренда стартует 9 дней назад, клиент оплатил неделю (3500₽) —
     * долг ровно 1000₽, переноса нет если оплачено только за фактически
     * использованные дни. Проверяет, что наличие платежей в ledger
     * не ломает балансировку.
     */
    @Test
    fun `delete fully covered rental should keep carriedDebt at zero`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Fully Covered Carry", phone = "79009990006")
        val bikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "CARRY-COVER-FRAME",
            motorSerial = "CARRY-COVER-MOTOR",
            weeklyRateRub = 3500
        )
        val start = LocalDate.now().minusDays(7).toString()

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"carry.cover",
                  "password":"carry123",
                  "period_start":"$start"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")

        paySingleWeek(login = "carry.cover", password = "carry123", externalId = "provider-cover-1")

        val deleteRental = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)

        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val body = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals(
            0,
            body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt(),
            "carried_debt_rub must be 0 when used_days == covered_days"
        )
    }

    @Test
    fun `delete with overdue days should carry per-day debt to client`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Carry Per Day", phone = "79009990002")
        val bikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "CARRY-PERDAY-FRAME",
            motorSerial = "CARRY-PERDAY-MOTOR",
            weeklyRateRub = 3500
        )
        val start = LocalDate.now().minusDays(9).toString()

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"carry.perday",
                  "password":"carry123",
                  "period_start":"$start"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")

        // Клиент логинится и оплачивает ровно неделю (3500₽).
        val clientLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"carry.perday","password":"carry123"}""")
        }
        assertEquals(HttpStatusCode.OK, clientLogin.status)
        val clientToken = json.parseToJsonElement(clientLogin.bodyAsText())
            .jsonObject["access_token"]?.jsonPrimitive?.content ?: error("No client token")

        val createPayment = client.post("/api/v1/payments/create") {
            bearerAuth(clientToken)
            contentType(ContentType.Application.Json)
            setBody("""{"payment_type":"week"}""")
        }
        assertEquals(HttpStatusCode.OK, createPayment.status)
        val paymentId = json.parseToJsonElement(createPayment.bodyAsText())
            .jsonObject["payment_id"]?.jsonPrimitive?.content ?: error("No payment_id")

        val webhook = client.post("/api/v1/payments/yookassa/webhook") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "type":"notification",
                  "event":"payment.succeeded",
                  "object":{
                    "id":"provider-carry-1",
                    "status":"succeeded",
                    "metadata":{"local_payment_id":"$paymentId"}
                  }
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, webhook.status)

        // Удаление: covered=7, used=9 → overdue 2 дня * 500 = 1000 ₽ финального долга.
        val deleteRental = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)

        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val body = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals(
            1000,
            body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt(),
            "carried_debt_rub must be exactly per-day overdue (1000 ₽)"
        )

        // Карточка lifecycle-аренды исчезла с главного экрана.
        val rents = client.get("/api/v1/admin/rents") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, rents.status)
        assertTrue(
            json.parseToJsonElement(rents.bodyAsText()).jsonArray.none { item ->
                item.jsonObject["rental_id"]?.jsonPrimitive?.content == rentalId
            }
        )

        // Старый логин клиента продолжает открывать завершённую клиентскую аренду.
        val historicalLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"carry.perday","password":"carry123"}""")
        }
        assertEquals(HttpStatusCode.OK, historicalLogin.status)
    }

    @Test
    fun `delete in_stock lifecycle should not change carriedDebt`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "In Stock Then Delete", phone = "79009990003")
        val bikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "INSTOCK-DEL-FRAME",
            motorSerial = "INSTOCK-DEL-MOTOR",
            weeklyRateRub = 3500
        )
        val start = LocalDate.now().minusDays(9).toString()

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"instock.del",
                  "password":"instock123",
                  "period_start":"$start"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")

        // Переводим lifecycle в in_stock (закрывает активную client_rental).
        val finish = client.post("/api/v1/admin/rentals/$rentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finish.status)

        // Удаление уже-IN_STOCK lifecycle: активной client_rental нет, переноса нет.
        val deleteRental = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)

        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, details.status)
        val body = json.parseToJsonElement(details.bodyAsText()).jsonObject
        assertEquals(
            0,
            body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt(),
            "Deleting already-in_stock lifecycle must not change carried_debt_rub"
        )
    }

    @Test
    fun `deleting already-deleted rental should return 404`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Double Delete", phone = "79009990004")
        val bikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "DOUBLE-DEL-FRAME",
            motorSerial = "DOUBLE-DEL-MOTOR"
        )

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"double.del",
                  "password":"double123",
                  "period_start":"${LocalDate.now()}"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")

        val firstDelete = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, firstDelete.status)

        val secondDelete = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.NotFound, secondDelete.status)
    }

    @Test
    fun `delete should accumulate carriedDebt across multiple deleted lifecycles`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(adminToken, fullName = "Accumulate Carry", phone = "79009990005")

        // Цикл 1: создаём аренду на 9 дней назад, оплачиваем неделю (1000 долга).
        val bike1 = createBikeAndGetId(
            adminToken,
            frameSerial = "ACC-1-FRAME",
            motorSerial = "ACC-1-MOTOR",
            weeklyRateRub = 3500
        )
        val start1 = LocalDate.now().minusDays(9).toString()
        val createRental1 = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bike1",
                  "login":"acc.cycle.1",
                  "password":"acc-cycle-1-pwd",
                  "period_start":"$start1"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental1.status)
        val rental1 = json.parseToJsonElement(createRental1.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id (1)")
        paySingleWeek(login = "acc.cycle.1", password = "acc-cycle-1-pwd", externalId = "provider-acc-1")
        val delete1 = client.post("/api/v1/admin/rentals/$rental1/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, delete1.status)

        val afterFirst = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(
            1000,
            json.parseToJsonElement(afterFirst.bodyAsText())
                .jsonObject["carried_debt_rub"]?.jsonPrimitive?.content?.toInt()
        )

        // Цикл 2: новая аренда на новом велосипеде, ещё 9 дней назад, ещё одна неделя оплачена.
        val bike2 = createBikeAndGetId(
            adminToken,
            frameSerial = "ACC-2-FRAME",
            motorSerial = "ACC-2-MOTOR",
            weeklyRateRub = 3500
        )
        val start2 = LocalDate.now().minusDays(9).toString()
        val createRental2 = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bike2",
                  "login":"acc.cycle.2",
                  "password":"acc-cycle-2-pwd",
                  "period_start":"$start2"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental2.status)
        val rental2 = json.parseToJsonElement(createRental2.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id (2)")
        paySingleWeek(login = "acc.cycle.2", password = "acc-cycle-2-pwd", externalId = "provider-acc-2")
        val delete2 = client.post("/api/v1/admin/rentals/$rental2/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, delete2.status)

        val afterSecond = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(
            2000,
            json.parseToJsonElement(afterSecond.bodyAsText())
                .jsonObject["carried_debt_rub"]?.jsonPrimitive?.content?.toInt(),
            "carried_debt_rub must accumulate across multiple lifecycle deletions"
        )
    }

    // ---------------------------------------------------------------------------
    // POST /admin/clients/{id}/carried-debt:
    //   admin-операции списания/приёма наличной оплаты по перенесённому долгу
    //   (docs/14_rental_lifecycle.md §7).
    // Каждый тест сначала создаёт+удаляет lifecycle, чтобы получить
    // ненулевой carriedDebt у клиента.
    // ---------------------------------------------------------------------------

    @Test
    fun `carriedDebt writeoff should reduce carriedDebt and audit ledger`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Writeoff", phone = "79009991001")

        // carriedDebt = 1000 (9 дней, оплачено 7 дней).
        val writeoff = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":400,"kind":"writeoff","comment":"акция"}""")
        }
        assertEquals(HttpStatusCode.OK, writeoff.status)
        val body = json.parseToJsonElement(writeoff.bodyAsText()).jsonObject
        assertEquals(600, body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(400, body["applied_to_carried_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, body["applied_to_active_rental_rub"]?.jsonPrimitive?.content?.toInt())
        assertTrue(body["active_rental_id"]?.jsonPrimitive?.contentOrNull == null)

        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(
            600,
            json.parseToJsonElement(details.bodyAsText()).jsonObject["carried_debt_rub"]?.jsonPrimitive?.content?.toInt()
        )
    }

    @Test
    fun `carriedDebt writeoff should reject amount exceeding carried debt`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Overflow", phone = "79009991002")

        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":5000,"kind":"writeoff"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "amount_rub exceeds carried_debt_rub",
            json.parseToJsonElement(response.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )

        // Сумма долга не изменилась.
        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(
            1000,
            json.parseToJsonElement(details.bodyAsText()).jsonObject["carried_debt_rub"]?.jsonPrimitive?.content?.toInt()
        )
    }

    @Test
    fun `carriedDebt payment full should reduce carriedDebt to zero`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Payment Full", phone = "79009991003")

        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":1000,"kind":"payment","comment":"наличные"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(1000, body["applied_to_carried_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, body["applied_to_active_rental_rub"]?.jsonPrimitive?.content?.toInt())
        assertTrue(body["active_rental_id"]?.jsonPrimitive?.contentOrNull == null)
    }

    @Test
    fun `carriedDebt payment partial should reduce carriedDebt partially`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Payment Partial", phone = "79009991004")

        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":300,"kind":"payment"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(700, body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(300, body["applied_to_carried_rub"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `carriedDebt payment excess should overflow into active client rental`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Excess", phone = "79009991005")

        // У клиента уже carriedDebt=1000 после удаления первой аренды.
        // Создаём НОВУЮ активную аренду на новом велосипеде, чтобы излишек было куда направить.
        val newBikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "EXCESS-FRAME",
            motorSerial = "EXCESS-MOTOR",
            weeklyRateRub = 3500
        )
        val today = LocalDate.now().toString()
        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$newBikeId",
                  "login":"excess.new",
                  "password":"excess123",
                  "period_start":"$today"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val newClientRentalId = json.parseToJsonElement(
            client.get("/api/v1/admin/clients/$clientId") { bearerAuth(adminToken) }.bodyAsText()
        ).jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { it.jsonObject["bike_id"]?.jsonPrimitive?.content == newBikeId }
            ?.jsonObject?.get("rental_id")?.jsonPrimitive?.content
            ?: error("No active client rental id")

        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            // amount=1500, carriedDebt=1000 → 1000 в carried, 500 в активную аренду.
            setBody("""{"amount_rub":1500,"kind":"payment","comment":"перевод"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["carried_debt_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(1000, body["applied_to_carried_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(500, body["applied_to_active_rental_rub"]?.jsonPrimitive?.content?.toInt())
        assertEquals(
            newClientRentalId,
            body["active_rental_id"]?.jsonPrimitive?.content,
            "excess must be routed to active client_rental.id"
        )
    }

    @Test
    fun `carriedDebt payment excess without active rental should fail`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Excess No Rental", phone = "79009991006")

        // У клиента нет активной аренды. carriedDebt = 1000, пытаемся внести 1500.
        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":1500,"kind":"payment"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val message = json.parseToJsonElement(response.bodyAsText())
            .jsonObject["message"]?.jsonPrimitive?.content
            ?: error("no message")
        assertTrue(
            message.contains("exceeds carried_debt_rub") && message.contains("no active rental"),
            "Expected reason about no active rental, got: $message"
        )

        // carriedDebt не тронут.
        val details = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        assertEquals(
            1000,
            json.parseToJsonElement(details.bodyAsText()).jsonObject["carried_debt_rub"]?.jsonPrimitive?.content?.toInt()
        )
    }

    @Test
    fun `carriedDebt should reject unknown kind`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Unknown Kind", phone = "79009991007")

        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":100,"kind":"refund"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "kind must be writeoff or payment",
            json.parseToJsonElement(response.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `carriedDebt should reject non-positive amount`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Zero", phone = "79009991008")

        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":0,"kind":"writeoff"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "amount_rub must be positive",
            json.parseToJsonElement(response.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `carriedDebt should require admin auth`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = setupCarriedDebt(adminToken = adminToken, fullName = "Carry Unauth", phone = "79009991009")

        val response = client.post("/api/v1/admin/clients/$clientId/carried-debt") {
            contentType(ContentType.Application.Json)
            setBody("""{"amount_rub":100,"kind":"writeoff"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    /**
     * Создаёт клиента, велосипед, аренду на 9 дней назад, оплачивает неделю
     * и удаляет lifecycle-карточку. После этого у клиента carriedDebtRub = 1000.
     * Возвращает clientId для удобства тестов carriedDebt-эндпоинта.
     */
    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.setupCarriedDebt(
        adminToken: String,
        fullName: String,
        phone: String
    ): String {
        val clientId = createClientAndGetId(adminToken, fullName = fullName, phone = phone)
        // Используем phone как уникальный суффикс серийников, чтобы тесты
        // не конфликтовали между собой.
        val suffix = phone.takeLast(6)
        val bikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "SETUP-$suffix-F",
            motorSerial = "SETUP-$suffix-M",
            weeklyRateRub = 3500
        )
        val start = LocalDate.now().minusDays(9).toString()
        val loginValue = "setup.carry.$suffix"
        val passwordValue = "setup$suffix"

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"$loginValue",
                  "password":"$passwordValue",
                  "period_start":"$start"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val rentalId = json.parseToJsonElement(createRental.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")

        paySingleWeek(login = loginValue, password = passwordValue, externalId = "provider-setup-$suffix")

        val deleteRental = client.post("/api/v1/admin/rentals/$rentalId/delete") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, deleteRental.status)
        return clientId
    }

    // ---------------------------------------------------------------------------
    // Инвариант: у каждой client_rental должны быть непустые логин и пароль
    // (docs/14_rental_lifecycle.md §4). Это покрывает и активную, и закрытую.
    // ---------------------------------------------------------------------------

    @Test
    fun `every client rental must expose credentials including after closure`() = testApplication {
        application { module() }
        val adminToken = loginAsAdmin()
        val clientId = createClientAndGetId(
            adminToken,
            fullName = "Credentials Invariant",
            phone = "79009992001"
        )
        val bikeId = createBikeAndGetId(
            adminToken,
            frameSerial = "CRED-INV-FRAME",
            motorSerial = "CRED-INV-MOTOR"
        )
        val start = LocalDate.now().minusDays(3).toString()

        val createRental = client.post("/api/v1/admin/rentals") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_id":"$clientId",
                  "bike_id":"$bikeId",
                  "login":"cred.invariant",
                  "password":"credInv123",
                  "period_start":"$start"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createRental.status)
        val lifecycleRentalId = json.parseToJsonElement(createRental.bodyAsText())
            .jsonObject["rental_id"]?.jsonPrimitive?.content ?: error("No rental_id")

        // Активная аренда — credentials видны через details.
        val activeDetails = client.get("/api/v1/admin/rentals/$lifecycleRentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, activeDetails.status)
        val activeBody = json.parseToJsonElement(activeDetails.bodyAsText()).jsonObject
        assertEquals("cred.invariant", activeBody["client_login"]?.jsonPrimitive?.content)
        assertEquals("credInv123", activeBody["client_password"]?.jsonPrimitive?.content)

        // Завершаем аренду (transition в in_stock) — закрытая client_rental
        // должна сохранить логин и пароль.
        val finish = client.post("/api/v1/admin/rentals/$lifecycleRentalId/finish") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, finish.status)

        // Получаем id закрытой client_rental из истории клиента.
        val clientDetails = client.get("/api/v1/admin/clients/$clientId") {
            bearerAuth(adminToken)
        }
        val closedClientRentalId = json.parseToJsonElement(clientDetails.bodyAsText())
            .jsonObject["rentals"]?.jsonArray
            ?.firstOrNull { it.jsonObject["bike_id"]?.jsonPrimitive?.content == bikeId }
            ?.jsonObject?.get("rental_id")?.jsonPrimitive?.content
            ?: error("No closed client rental id")

        val closedDetails = client.get("/api/v1/admin/rentals/$closedClientRentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, closedDetails.status)
        val closedBody = json.parseToJsonElement(closedDetails.bodyAsText()).jsonObject
        assertEquals(
            "cred.invariant",
            closedBody["client_login"]?.jsonPrimitive?.content,
            "closed client_rental must keep its historical login"
        )
        assertEquals(
            "credInv123",
            closedBody["client_password"]?.jsonPrimitive?.content,
            "closed client_rental must keep its historical password"
        )

        // Bonus: lifecycle сейчас IN_STOCK. Открытие тоже не должно
        // ломать ответ для закрытой client_rental.
        val lifecycleDetails = client.get("/api/v1/admin/rentals/$lifecycleRentalId") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, lifecycleDetails.status)
        val lifecycleBody = json.parseToJsonElement(lifecycleDetails.bodyAsText()).jsonObject
        assertEquals(
            "in_stock",
            lifecycleBody["rental_pipeline_status"]?.jsonPrimitive?.content
        )
        // У lifecycle в in_stock активной client_rental нет — поля client_login
        // и client_password могут быть null/empty (это сам lifecycle, не закрытая аренда).
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.paySingleWeek(
        login: String,
        password: String,
        externalId: String
    ) {
        val clientLogin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$login","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, clientLogin.status)
        val clientToken = json.parseToJsonElement(clientLogin.bodyAsText())
            .jsonObject["access_token"]?.jsonPrimitive?.content ?: error("No client token")

        val createPayment = client.post("/api/v1/payments/create") {
            bearerAuth(clientToken)
            contentType(ContentType.Application.Json)
            setBody("""{"payment_type":"week"}""")
        }
        assertEquals(HttpStatusCode.OK, createPayment.status)
        val paymentId = json.parseToJsonElement(createPayment.bodyAsText())
            .jsonObject["payment_id"]?.jsonPrimitive?.content ?: error("No payment_id")

        val webhook = client.post("/api/v1/payments/yookassa/webhook") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "type":"notification",
                  "event":"payment.succeeded",
                  "object":{
                    "id":"$externalId",
                    "status":"succeeded",
                    "metadata":{"local_payment_id":"$paymentId"}
                  }
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, webhook.status)
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

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.loginAsAdminIp(): String {
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"admin_ip","password":"adminip123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        return json.parseToJsonElement(login.bodyAsText())
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("No admin IP token")
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createClientAndGetId(
        adminToken: String,
        fullName: String = "Roman Sergeev",
        phone: String = "89859325907",
        email: String? = null
    ): String {
        val emailContact = email?.let {
            """,
                  {"label":"Email","number":"$it"}"""
        }.orEmpty()
        val createClient = client.post("/api/v1/admin/clients") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
	                  "full_name":"$fullName",
	                  "address":"Moscow, 123",
	                  "passport_data":"1234 567890",
	                  "phones":[{"label":"Рабочий (TG)","number":"$phone"}$emailContact]
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

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createBikeAndGetId(
        adminToken: String,
        frameSerial: String = "FRM-3200",
        motorSerial: String = "MTR-3200",
        weeklyRateRub: Int = 3200,
        batterySerial1: String? = null,
        batterySerial2: String? = null
    ): String {
        // По умолчанию серийники батарей привязываются к frameSerial, чтобы
        // несколько вызовов helper в одном тесте не ловили "duplicate serial".
        val battery1 = batterySerial1 ?: "BAT1-$frameSerial"
        val battery2 = batterySerial2 ?: "BAT2-$frameSerial"
        val createBike = client.post("/api/v1/admin/bikes") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "photo_url":"https://example.com/bikes/monster.png",
                  "bike_model":"Монстер",
                  "weekly_rate_rub":$weeklyRateRub,
	                  "frame_serial_number":"$frameSerial",
	                  "motor_serial_number":"$motorSerial",
                  "battery_serial_number_1":"$battery1",
                  "battery_serial_number_2":"$battery2"
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
