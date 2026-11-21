// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.support

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

class FixtureServer : Dispatcher() {
    data class Call(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    private val overrides = ConcurrentHashMap<String, () -> MockResponse>()
    val calls = CopyOnWriteArrayList<Call>()
    val server = MockWebServer().apply { dispatcher = this@FixtureServer }

    val url: String
        get() = server.url("/").toString()

    fun start() = server.start()

    fun shutdown() = server.close()

    fun respond(method: String, path: String, body: String, code: Int = 200) {
        overrides["$method /api/v1/$path"] = { json(body, code) }
    }

    fun respond(method: String, path: String, response: () -> MockResponse) {
        overrides["$method /api/v1/$path"] = response
    }

    fun fail(method: String, path: String, code: Int, detail: String, problem: String? = null) {
        val extra = problem?.let { ",\"code\":\"$it\"" }.orEmpty()
        respond(method, path, """{"status":$code,"detail":"$detail"$extra}""", code)
    }

    fun calls(method: String, path: String): List<Call> = calls.filter { it.method == method && it.path == "/api/v1/$path" }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        calls +=
            Call(
                request.method,
                path,
                request.url.encodedQuery,
                request.headers.associate { it.first to it.second },
                request.body?.utf8().orEmpty(),
            )
        overrides["${request.method} $path"]?.let { return it() }
        val route = path.removePrefix("/api/v1/")
        return route(request.method, route.split('/'))?.let { json(it) } ?: json("""{"status":404,"detail":"Not found: $route"}""", 404)
    }

    private fun route(method: String, parts: List<String>): String? {
        val joined = parts.joinToString("/")
        return when ("$method $joined") {
            "GET public/mobile/configuration" -> fixture("configuration")
            "GET auth/csrf" -> fixture("csrf")
            "POST auth/app/signin" -> signIn(eulaAccepted = true)
            "GET auth/me" -> principal(eulaAccepted = true)
            "GET auth/eula" -> fixture("eula")
            "POST auth/signon" -> """{"message":"ok"}"""
            "GET workspace" -> fixture("workspace")
            "GET account", "PUT account" -> fixture("account")
            "GET account/sites" -> fixture("sites")
            "GET account/stations" -> fixture("stations")
            "GET account/tokens" -> fixture("tokens")
            "GET account/card-offer" -> fixture("card_offer")
            "GET account/card-orders" -> fixture("card_orders")
            "POST account/card-orders/activate" -> fixture("card_activated")
            "GET account/cars" -> fixture("cars")
            "POST account/cars" -> """{"id":"27e97d10-b40a-4f65-b976-840049b5fc56","revision":0}"""
            "GET account/car-catalog" -> fixture("catalog")
            "GET account/autocharge/candidates" -> fixture("autocharge")
            "GET account/notifications", "PUT account/notifications" -> fixture("preferences")
            "GET account/payment-methods" -> PAYMENT_METHODS
            "POST account/payment-methods/checkout" -> """{"url":"https://checkout.stripe.com/c/pay/test"}"""
            "GET transactions" -> fixture("sessions")
            "POST transactions/start" -> fixture("receipt")
            "GET statistics/years" -> "[2026,2025]"
            "GET notifications" -> fixture("notifications")
            "GET notifications/types" -> fixture("types")
            "GET invoices" -> fixture("invoices")
            else -> dynamic(method, parts)
        }
    }

    private fun dynamic(method: String, parts: List<String>): String? {
        val section = parts.getOrNull(0)
        val kind = parts.getOrNull(1)
        return when {
            method != "GET" && section == "auth" -> SUCCESS
            method == "DELETE" -> SUCCESS
            method == "POST" && section == "account" && kind in setOf("devices", "payment-methods") -> SUCCESS
            method == "POST" && section == "notifications" -> SUCCESS
            section == "account" && kind == "stations" && parts.size == 3 -> fixture("station")
            section == "account" && kind == "stations" && parts.size == 4 && method == "GET" -> fixture("photos")
            section == "account" && kind == "stations" && parts.size == 4 -> fixture("photos").trim().removePrefix("[").removeSuffix("]")
            section == "account" && kind == "stations" && parts.size == 5 -> null
            section == "account" && kind == "cars" && parts.size == 3 && method == "PUT" -> """{"id":"${parts[2]}","revision":1}"""
            section == "account" && kind == "cars" && parts.getOrNull(3) == "autocharge" && method == "POST" ->
                """{"uid":"VID:ABCDEF","vehicle_id":"${parts[2]}","enabled":true}"""
            section == "account" && kind == "cars" && parts.getOrNull(3) == "autocharge" -> "[]"
            section == "account" && kind == "tokens" && parts.getOrNull(3) == "google-wallet-pass" ->
                """{"jwt":"a.b.c","save_url":"https://pay.google.com/gp/v/save/a.b.c"}"""
            section == "public" && kind == "workspaces" && parts.size == 4 -> fixture("public_stations")
            section == "public" && kind == "workspaces" && parts.size == 5 -> fixture("station")
            section == "public" && kind == "evses" && parts.size == 4 -> fixture("portal")
            section == "public" && kind == "evses" && parts.getOrNull(4) == "checkout" ->
                """{"orderId":"7084987b-6653-4d4a-99b7-c1e925397ec2","checkoutUrl":"https://checkout.stripe.com/c/pay/guest"}"""
            section == "public" && kind == "sessions" -> fixture("guest")
            section == "transactions" && parts.size == 2 -> fixture("session")
            section == "transactions" && kind != null && parts.getOrNull(2) == "consumptions" -> fixture("consumptions")
            section == "transactions" && kind != null && parts.getOrNull(2) == "signed-data" ->
                """{"session_id":${parts[1]},"station_id":"CONTRAT-1","format":"OCMF","start":"OCMF|start","stop":"OCMF|stop"}"""
            section == "transactions" && kind != null && parts.getOrNull(2) == "stop" -> fixture("receipt")
            section == "statistics" && parts.size == 2 -> fixture("statistics")
            section == "invoices" && parts.size == 3 -> """{"url":"https://invoices.stripe.com/i/test"}"""
            else -> null
        }
    }

    companion object {
        const val WORKSPACE = "contrat-c887fa45-d02b-4592-a15f-e9d2a8033eea"
        const val SUCCESS = """{"success":true}"""
        const val PAYMENT_METHODS =
            """[{"id":"pm_1","brand":"visa","last4":"4242","exp_month":8,"exp_year":2029,"default":true,"created_at":"2026-01-10T10:00:00Z"},""" +
                """{"id":"pm_2","brand":"mastercard","last4":"4444","exp_month":3,"exp_year":2028,"default":false,"created_at":"2026-05-02T10:00:00Z"}]"""

        fun fixture(name: String): String = File("src/test/resources/fixtures/$name.json").readText()

        fun signIn(eulaAccepted: Boolean): String = fixture("signin").replace("\"eulaAccepted\": false", "\"eulaAccepted\": $eulaAccepted")

        fun principal(eulaAccepted: Boolean, role: String = "DRIVER"): String =
            signIn(eulaAccepted).substringAfter("\"user\": ").trim().removeSuffix("}").replace("\"DRIVER\"", "\"$role\"")

        fun json(body: String, code: Int = 200): MockResponse =
            MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()
    }
}
