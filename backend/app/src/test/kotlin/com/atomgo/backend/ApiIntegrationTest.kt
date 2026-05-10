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
        assertEquals(createdBikeId, rentalBody["bike_id"]?.jsonPrimitive?.content)
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
                  "password":"client123",
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
                  "password":"client123",
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
    fun `finish rental should detach client auth and move rent to mine state`() = testApplication {
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
                  "password":"client123",
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
            setBody("""{"login":"detach.client","password":"client123"}""")
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

        val rentsAfterFinish = client.get("/api/v1/admin/rents") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, rentsAfterFinish.status)
        val rentEntry = json.parseToJsonElement(rentsAfterFinish.bodyAsText())
            .jsonArray
            .firstOrNull { it.jsonObject["client_id"]?.jsonPrimitive?.content == clientId }
            ?.jsonObject
            ?: error("Client rent not found after finish")
        assertEquals(false, rentEntry["rental_is_active"]?.jsonPrimitive?.content?.toBooleanStrict())

        val dashboardWithOldToken = client.get("/api/v1/client/me/dashboard") {
            bearerAuth(clientToken)
        }
        assertEquals(HttpStatusCode.Unauthorized, dashboardWithOldToken.status)

        val loginAfterDetach = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"detach.client","password":"client123"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, loginAfterDetach.status)
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
                  "password":"client123",
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
            setBody("""{"login":"client.from.details","password":"client123"}""")
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
                  "password":"client123",
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
            setBody("""{"login":"ip.client","password":"client123"}""")
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
                  "password":"client123",
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
        motorSerial: String = "MTR-3200"
    ): String {
        val createBike = client.post("/api/v1/admin/bikes") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "photo_url":"https://example.com/bikes/monster.png",
                  "bike_model":"Монстер",
                  "weekly_rate_rub":3200,
	                  "frame_serial_number":"$frameSerial",
	                  "motor_serial_number":"$motorSerial",
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
