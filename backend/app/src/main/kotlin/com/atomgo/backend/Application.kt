package com.atomgo.backend

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.PaymentType
import com.atomgo.backend.domain.PricingRules
import com.atomgo.backend.domain.Role
import com.atomgo.backend.infra.AuthService
import com.atomgo.backend.infra.InMemoryStore
import com.atomgo.backend.infra.PaymentService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    val store = InMemoryStore.seed()
    val authService = AuthService(store)
    val paymentService = PaymentService(store)

    routing {
        get("/") {
            call.respond(mapOf("service" to "Atom Go API", "version" to "0.1.0"))
        }
        get("/health/live") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "live"))
        }
        get("/health/ready") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
        }

        route("/api/v1") {
            post("/auth/login") {
                val request = call.receive<JsonObject>()
                val login = request.string("login")
                val password = request.string("password")

                if (login.isNullOrBlank() || password.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "login and password are required"))
                    return@post
                }

                val auth = authService.login(login, password)
                if (auth == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Неверный логин или пароль"))
                    return@post
                }
                val (token, session) = auth
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "access_token" to token,
                        "role" to authService.roleToApi(session.role),
                        "user_id" to session.userId
                    )
                )
            }

            get("/client/me/dashboard") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.CLIENT || session.clientId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                    return@get
                }

                val client = store.clients.first { it.id == session.clientId }
                val debt = LedgerCalculator.debtRub(client, store.ledger, LocalDate.now())
                val paidUntil = LedgerCalculator.paidUntilDate(client, store.ledger)
                val totalAdjustment = LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)

                call.respond(
                    mapOf(
                        "client_id" to client.id,
                        "bike_model" to client.bikeModel,
                        "rental_start" to client.rentalStartDate.toString(),
                        "paid_until" to paidUntil.toString(),
                        "debt_rub" to debt,
                        "total_adjustment_rub" to totalAdjustment,
                        "presets" to mapOf(
                            "day_rub" to PricingRules.dayAmount(client.weeklyRateRub),
                            "week_rub" to PricingRules.weekAmount(client.weeklyRateRub),
                            "two_weeks_rub" to PricingRules.twoWeeksAmount(client.weeklyRateRub),
                            "month_rub" to PricingRules.monthAmount(client.weeklyRateRub),
                            "debt_exact_rub" to debt
                        )
                    )
                )
            }

            get("/admin/clients") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                    return@get
                }

                val now = LocalDate.now()
                val response = store.clients.map { client ->
                    val debt = LedgerCalculator.debtRub(client, store.ledger, now)
                    val paid = LedgerCalculator.totalPaidRub(store.ledger, client.id)
                    val profit = if (debt == 0) paid else 0
                    val paidUntil = LedgerCalculator.paidUntilDate(client, store.ledger)
                    val dayDiff = ChronoUnit.DAYS.between(now, paidUntil).toInt()
                    val statusText = if (dayDiff >= 0) {
                        "Оплачено еще на $dayDiff дн."
                    } else {
                        "Долг за ${-dayDiff} дн."
                    }

                    mapOf(
                        "client_id" to client.id,
                        "full_name" to client.fullName,
                        "bike_model" to client.bikeModel,
                        "bike_avatar_url" to client.bikeAvatarUrl,
                        "status_text" to statusText,
                        "debt_rub" to debt,
                        "profit_rub" to profit,
                        "total_adjustment_rub" to LedgerCalculator.totalAdjustmentRub(store.ledger, client.id)
                    )
                }
                call.respond(response)
            }

            post("/payments/create") {
                val session = authService.resolveSession(call.request.header("Authorization"))
                if (session == null || session.role != Role.CLIENT || session.clientId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                    return@post
                }

                val request = call.receive<JsonObject>()
                val type = PaymentType.fromApi(request.string("payment_type") ?: "")
                if (type == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Unknown payment_type"))
                    return@post
                }

                try {
                    val payment = paymentService.createPayment(clientId = session.clientId, paymentType = type)
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "payment_id" to payment.id,
                            "amount_rub" to payment.amountRub,
                            "confirmation_url" to payment.confirmationUrl,
                            "idempotence_key" to payment.idempotenceKey,
                            "status" to payment.status.name.lowercase()
                        )
                    )
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid payment")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request")))
                }
            }

            post("/payments/yookassa/webhook") {
                val webhook = call.receive<JsonObject>()
                val event = webhook.string("event")
                val obj = webhook["object"]?.jsonObject
                val providerPaymentId = obj?.string("id")
                val metadata = obj?.get("metadata")?.jsonObject
                val localPaymentId = metadata?.string("local_payment_id")

                if (event.isNullOrBlank() || providerPaymentId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid webhook payload"))
                    return@post
                }

                val result = paymentService.applyWebhook(
                    event = event,
                    providerPaymentId = providerPaymentId,
                    localPaymentId = localPaymentId
                )

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "applied" to result.applied,
                        "message" to result.message,
                        "payment_id" to result.paymentId,
                        "client_id" to result.clientId,
                        "debt_rub" to result.debtRub
                    )
                )
            }
        }
    }
}
