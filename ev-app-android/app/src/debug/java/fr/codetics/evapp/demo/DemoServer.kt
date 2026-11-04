// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.demo

import java.time.Instant
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl

private fun element(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to element(item) })
        is List<*> -> JsonArray(value.map(::element))
        else -> JsonPrimitive(value.toString())
    }

private fun Double.places(count: Int): Double {
    val factor = 10.0.pow(count)
    return (this * factor).roundToInt() / factor
}

private fun now() = Instant.now()

private fun Instant.text() = toString()

private fun uuid() = UUID.randomUUID().toString()

class DemoServer {
    private class Live(val id: Int, val station: String, val connector: Int, val token: String, val startedAt: Instant)

    private class Guest(val id: String, val credit: Int, val createdAt: Instant, var stoppedAt: Instant? = null)

    private class Photo(val id: String, val station: String, val data: ByteArray, val mine: Boolean, val createdAt: Instant)

    private val carId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
    private var live: Live? = null
    private val cars = mutableListOf<MutableMap<String, Any?>>()
    private var defaultCar: String? = carId
    private var preferences: Map<String, Any?> = mapOf("email" to true, "push" to true, "types" to emptyMap<String, Boolean>())
    private val guests = mutableMapOf<String, Guest>()
    private val photos = mutableListOf<Photo>()
    private var nextSession = 2150
    private var cardActivated = false
    private val finished: MutableList<Map<String, Any?>>

    init {
        cars +=
            mutableMapOf(
                "id" to carId,
                "name" to "Renault Megane E-Tech",
                "revision" to 1,
                "updated_at" to "2026-10-20T08:00:00Z",
                "data" to
                    mapOf(
                        "userId" to DemoSupport.USER_ID,
                        "make" to "Renault",
                        "model" to "Megane E-Tech EV60",
                        "licensePlate" to "GH-482-TZ",
                        "batteryKwh" to 60,
                        "maxAcPowerKw" to 22,
                        "maxDcPowerKw" to 130,
                        "default" to true,
                    ),
            )
        finished = (0 until 9).map(::history).toMutableList()
    }

    private fun json(value: Any?, status: Int = 200) = Triple(status, element(value).toString().encodeToByteArray(), "application/json")

    private fun problem(status: Int, detail: String) = json(mapOf("status" to status, "title" to "Error", "detail" to detail), status)

    private fun input(body: ByteArray?): JsonObject =
        body?.takeIf { it.isNotEmpty() }?.let { runCatching { Json.parseToJsonElement(it.decodeToString()).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())

    private fun JsonObject.text(key: String) = get(key)?.jsonPrimitive?.contentOrNull

    private fun success() = json(mapOf("success" to true, "accepted" to true))

    @Synchronized
    fun handle(method: String, path: String, url: HttpUrl, body: ByteArray?): Triple<Int, ByteArray, String> {
        val parts = path.split('/').filter(String::isNotEmpty)
        if (parts.size < 2) return problem(404, "Not found")
        val route = parts.drop(2).joinToString("/")
        fun parameter(name: String) = url.queryParameter(name)
        when ("$method $route") {
            "GET public/mobile/configuration" ->
                return json(
                    mapOf(
                        "server" to "ev-supervision-lite", "api_version" to 1, "public_url" to "https://demo.ev-app.invalid",
                        "signup_enabled" to true, "public_tenant" to "demo", "push_enabled" to true, "wallet_enabled" to true, "wallet_nfc_enabled" to true,
                        "android_push_enabled" to true, "google_wallet_enabled" to true, "google_wallet_nfc_enabled" to true,
                    )
                )
            "GET auth/csrf" -> return json(mapOf("token" to "demo", "headerName" to "X-XSRF-TOKEN"))
            "POST auth/app/signin" -> {
                val credentials = input(body)
                if (credentials.text("email") != DemoSupport.EMAIL || credentials.text("password") != DemoSupport.PASSWORD) {
                    return problem(401, "Identifiants incorrects.")
                }
                return json(mapOf("token" to "demo-token-demo-token-demo-token-demo-token", "expires_at" to now().plusSeconds(2_592_000).text(), "user" to principal()))
            }
            "GET auth/me" -> return json(principal())
            "POST auth/signout", "POST auth/signout/all", "POST auth/eula/accept", "POST auth/signon", "POST auth/password/reset/request", "POST auth/mail/resend" -> return success()
            "GET auth/eula" -> return json(mapOf("version" to "1.0", "attribution" to "Supervision de recharge Lite — Kevin Vavelin, Codetics", "url" to "https://codetics.fr"))
            "GET workspace" ->
                return json(
                    mapOf(
                        "id" to DemoSupport.TENANT_ID, "slug" to "demo", "name" to "Réseau Démo", "active" to true, "logo_url" to null,
                        "components" to
                            mapOf(
                                "organization" to true, "pricing" to true, "billing" to true, "roaming" to false, "smart_charging" to false,
                                "statistics" to true, "assets" to false, "cars" to true, "public_charging" to true,
                            ),
                    )
                )
            "GET account", "PUT account" -> return json(account())
            "DELETE account" -> return success()
            "GET account/sites" -> return json(listOf(mapOf("site_id" to "c0000000-0000-4000-8000-000000000001", "name" to "Parking privé Codetics", "site_admin" to false, "site_owner" to false)))
            "GET account/tokens" -> return json(tokens())
            "GET account/card-offer" -> return json(mapOf("available" to true, "price_minor" to 1500, "currency" to "EUR", "description" to "Carte RFID du réseau", "shipping" to true))
            "GET account/card-orders" -> return json(listOf(cardOrder()))
            "POST account/card-orders/activate" -> {
                val number = input(body).text("number").orEmpty().uppercase().filter(Char::isLetterOrDigit)
                val accepted = listOf(DemoSupport.ORDERED_CARD, DemoSupport.ORDERED_CARD_NUMBER).map { it.filter(Char::isLetterOrDigit) }
                if (cardActivated || number !in accepted) return problem(404, "Aucune carte commandée sur votre compte ne correspond à ce numéro.")
                cardActivated = true
                return json(orderedCard())
            }
            "GET account/stations", "GET public/workspaces/demo/stations" -> return json(stations())
            "GET account/cars" -> return json(cars)
            "POST account/cars" -> {
                val record = input(body)
                val id = uuid()
                val data = (record["data"] as? JsonObject).orEmpty() + ("userId" to JsonPrimitive(DemoSupport.USER_ID))
                if (data["default"]?.jsonPrimitive?.booleanOrNull == true) defaultCar = id
                cars += mutableMapOf("id" to id, "name" to (record.text("name") ?: "Véhicule"), "revision" to 0, "updated_at" to now().text(), "data" to JsonObject(data))
                return json(mapOf("id" to id, "revision" to 0))
            }
            "GET account/car-catalog" -> return json(catalog(parameter("search").orEmpty()))
            "GET account/autocharge/candidates" -> return json(emptyList<Any>())
            "GET account/notifications" -> return json(normalizedPreferences())
            "PUT account/notifications" -> {
                val update = input(body)
                preferences =
                    mapOf(
                        "email" to update["email"], "push" to update["push"],
                        "types" to (update["types"] as? JsonObject).orEmpty().mapValues { (_, value) -> value.jsonPrimitive.booleanOrNull ?: true },
                    )
                return json(normalizedPreferences())
            }
            "POST account/devices" -> return success()
            "GET account/payment-methods" ->
                return json(
                    listOf(
                        mapOf("id" to "pm_demo1", "brand" to "visa", "last4" to "4242", "exp_month" to 8, "exp_year" to 2029, "default" to true, "created_at" to "2026-01-10T10:00:00Z"),
                        mapOf("id" to "pm_demo2", "brand" to "mastercard", "last4" to "4444", "exp_month" to 3, "exp_year" to 2028, "default" to false, "created_at" to "2026-05-02T10:00:00Z"),
                    )
                )
            "POST account/payment-methods/checkout" -> return json(mapOf("url" to "https://stripe.com"))
            "GET invoices" -> return json(mapOf("items" to invoices(), "total" to 3))
            "GET notifications" -> return json(mapOf("items" to notifications(), "total" to 4))
            "GET notifications/types" -> return json(notificationTypes())
            "POST notifications/report-error" -> return success()
            "GET transactions" ->
                return json(sessionsPage(parameter("status"), parameter("stationId"), parameter("offset")?.toIntOrNull() ?: 0, parameter("limit")?.toIntOrNull() ?: 30))
            "POST transactions/start" -> {
                val request = input(body)
                if (live != null) return problem(409, "Une session est déjà en cours sur ce connecteur.")
                nextSession += 1
                live = Live(nextSession, request.text("stationId").orEmpty(), request["connectorId"]?.jsonPrimitive?.intOrNull ?: 1, request.text("tokenUid").orEmpty(), now())
                return json(mapOf("id" to uuid(), "state" to "QUEUED"))
            }
            "GET statistics/years" -> return json(listOf(2026, 2025))
        }
        if (parts.size >= 5 && parts[2] == "account" && parts[3] == "stations") {
            val station = parts[4]
            if (parts.size == 5 && method == "GET") return detail(station)
            if (parts.size == 6 && parts[5] == "photos") {
                if (method == "GET") return json(photos.filter { it.station == station }.reversed().map(::photoJson))
                val photo = Photo(uuid(), station, body ?: ByteArray(0), true, now())
                photos += photo
                return json(photoJson(photo))
            }
            if (parts.size == 7 && parts[5] == "photos") {
                if (method == "DELETE") {
                    photos.removeAll { it.id == parts[6] }
                    return success()
                }
                photos.firstOrNull { it.id == parts[6] }?.let { return Triple(200, it.data, "image/jpeg") }
            }
        }
        if (parts.size == 7 && parts[2] == "public" && parts[3] == "workspaces" && parts[5] == "stations") return detail(parts[6])
        if (parts.size == 6 && parts[2] == "account" && parts[3] == "tokens" && parts[5] == "google-wallet-pass") {
            return problem(503, "La démonstration ne peut pas signer de passe : Google n’accepte que les passes signés par le compte de service de l’exploitant.")
        }
        if (parts.size >= 5 && parts[2] == "account" && parts[3] == "cars") {
            if (parts.size == 5 && method == "PUT") {
                val record = input(body)
                cars.firstOrNull { it["id"] == parts[4] }?.let {
                    it["name"] = record.text("name")
                    it["data"] = record["data"]
                }
                return json(mapOf("id" to parts[4], "revision" to 2))
            }
            if (parts.size == 5 && method == "DELETE") {
                cars.removeAll { it["id"] == parts[4] }
                return success()
            }
            if (parts.size == 6 && parts[5] == "autocharge") return json(emptyList<Any>())
        }
        if (parts.size == 5 && parts[2] == "account" && parts[3] == "devices") return success()
        if (parts.size >= 5 && parts[2] == "account" && parts[3] == "payment-methods") return success()
        if (parts.size == 5 && parts[2] == "invoices") return json(mapOf("url" to "https://stripe.com"))
        if (parts.size >= 4 && parts[2] == "transactions") {
            val id = parts[3].toIntOrNull()
            if (id != null) {
                if (parts.size == 4) return session(id)
                when (parts[4]) {
                    "consumptions" -> return json(consumptions(id))
                    "signed-data" -> return json(mapOf("session_id" to id, "station_id" to "PAR-01", "format" to "OCMF", "start" to """OCMF|{"FV":"1.0"}|{"SA":"ECDSA"}""", "stop" to """OCMF|{"FV":"1.0"}|{"SA":"ECDSA"}"""))
                    "stop" -> {
                        stopLive()
                        return json(mapOf("id" to uuid(), "state" to "QUEUED"))
                    }
                }
            }
        }
        if (parts.size == 4 && parts[2] == "statistics") return json(statistics(parts[3], parameter("year")?.toIntOrNull() ?: 2026))
        if (parts.size == 6 && parts[2] == "public" && parts[3] == "evses") return json(portal())
        if (parts.size == 7 && parts[2] == "public" && parts[3] == "evses" && parts[6] == "checkout") {
            val order = uuid()
            guests[order] = Guest(order, input(body)["creditMinor"]?.jsonPrimitive?.intOrNull ?: 2000, now())
            return json(mapOf("orderId" to order, "checkoutUrl" to "https://stripe.com"))
        }
        if (parts.size >= 5 && parts[2] == "public" && parts[3] == "sessions") {
            val guest = guests[parts[4]] ?: return problem(404, "Session introuvable.")
            if (parts.size == 6 && parts[5] == "stop") guest.stoppedAt = now()
            return json(guestJson(guest))
        }
        return problem(404, "Route de démonstration inconnue : $route")
    }

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

    private fun principal() =
        mapOf(
            "id" to DemoSupport.USER_ID, "tenantId" to DemoSupport.TENANT_ID, "email" to DemoSupport.EMAIL, "name" to "Thomas Bernard",
            "role" to "DRIVER", "platformAdmin" to false, "eulaAccepted" to true,
        )

    private fun account() =
        mapOf(
            "id" to DemoSupport.USER_ID, "email" to DemoSupport.EMAIL, "display_name" to "Thomas Bernard", "role" to "DRIVER", "enabled" to true,
            "email_verified" to true, "phone" to "+33 6 12 34 56 78", "locale" to "fr", "created_at" to "2025-03-12T10:00:00Z",
            "last_login_at" to now().text(), "billing_customer" to true, "default_car_id" to defaultCar, "site_count" to 1, "eula_accepted" to true,
        )

    private fun cardOrder() =
        mapOf(
            "id" to "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff", "state" to if (cardActivated) "ACTIVATED" else "SHIPPED", "amount_minor" to 1500, "currency" to "EUR",
            "created_at" to now().minusSeconds(432_000).text(), "paid_at" to now().minusSeconds(432_000).text(), "shipped_at" to now().minusSeconds(172_800).text(),
            "activated_at" to if (cardActivated) now().text() else null, "refunded" to false,
        )

    private fun token(uid: String, enabled: Boolean, visual: String, description: String, default: Boolean, used: String?) =
        mapOf(
            "uid" to uid, "user_id" to DemoSupport.USER_ID, "type" to "RFID", "enabled" to enabled, "expires_at" to null, "visual_id" to visual,
            "description" to description, "user_name" to "Thomas Bernard", "default" to default, "last_used_at" to used, "vehicle_id" to null, "vehicle_name" to null,
        )

    private fun orderedCard() = token(DemoSupport.ORDERED_CARD, true, DemoSupport.ORDERED_CARD_NUMBER, "Carte commandée", false, null)

    private fun tokens() =
        (if (cardActivated) listOf(orderedCard()) else emptyList()) +
            listOf(
                token(DemoSupport.OWN_CARD, true, "CARTE-1042", "Carte principale", true, now().minusSeconds(86_400).text()),
                token("04D5E6F7A8B990", false, "CARTE-0877", "Ancienne carte", false, "2025-11-02T09:00:00Z"),
            )

    private fun elapsed() = live?.let { (now().epochSecond - it.startedAt.epochSecond) * 60.0 } ?: 0.0

    private fun livePower() = (10.4 + sin(elapsed() / 300) * 0.6).places(2)

    private fun liveEnergy() = (elapsed() / 3600 * 10.8 + 3.2).places(2)

    private fun liveSoc() = min(100, 42 + (liveEnergy() / 60 * 100).toInt())

    private fun connector(id: Int, status: String, type: String, power: Double, station: String): Map<String, Any?> {
        val current = live
        val mine = current?.station == station && current.connector == id
        return mapOf(
            "connector_id" to id, "status" to if (mine) "Charging" else status, "error_code" to "NoError", "status_at" to now().minusSeconds(1200).text(),
            "type" to type, "maximum_power_kw" to power, "phases" to 3,
            "current_power_kw" to if (mine) livePower() else null, "current_transaction_id" to if (mine) current?.id else null,
            "current_energy_kwh" to if (mine) liveEnergy() else null, "current_soc" to if (mine) liveSoc() else null, "current_user_name" to null, "inactive_since" to null,
        )
    }

    private data class Plug(val id: Int, val status: String, val type: String, val power: Double)

    private data class Spot(val id: String, val name: String, val latitude: Double, val longitude: Double, val site: String, val address: String, val postal: String, val connected: Boolean, val plugs: List<Plug>, val evse: Boolean = false)

    private val spots =
        listOf(
            Spot("PAR-01", "Gare de Lyon — Borne A", 48.8443, 2.3744, "Gare de Lyon", "20 boulevard Diderot", "75012", true, listOf(Plug(1, "Available", "T2", 22.0), Plug(2, "Available", "T2", 22.0)), evse = true),
            Spot("PAR-02", "Bercy Village — Rapide", 48.8326, 2.3862, "Bercy Village", "28 rue François Truffaut", "75012", true, listOf(Plug(1, "Charging", "CCS", 150.0), Plug(2, "Available", "CCS", 150.0))),
            Spot("PAR-03", "Bastille — Parking", 48.8530, 2.3690, "Parking Bastille", "2 rue de la Roquette", "75011", true, listOf(Plug(1, "Charging", "T2", 11.0), Plug(2, "SuspendedEV", "T2", 11.0))),
            Spot("PAR-04", "République — Voirie", 48.8675, 2.3637, "Place de la République", "12 place de la République", "75003", true, listOf(Plug(1, "Faulted", "T2", 22.0))),
            Spot("PAR-05", "Hôtel de Ville", 48.8566, 2.3522, "Hôtel de Ville", "5 rue de Lobau", "75004", false, listOf(Plug(1, "Unavailable", "T2", 22.0))),
            Spot("PAR-06", "Nation — Rapide", 48.8484, 2.3958, "Place de la Nation", "1 avenue du Trône", "75011", true, listOf(Plug(1, "Available", "CCS", 50.0), Plug(2, "Preparing", "CHADEMO", 50.0))),
            Spot("PAR-07", "Montparnasse", 48.8422, 2.3219, "Gare Montparnasse", "17 boulevard de Vaugirard", "75015", true, listOf(Plug(1, "Available", "T2", 22.0))),
        )

    private fun station(spot: Spot, photoCount: Int?, tariff: Map<String, Any?>?) =
        mapOf(
            "id" to spot.id, "name" to spot.name, "vendor" to "Schneider Electric", "model" to "EVlink Pro", "connected" to spot.connected, "last_seen" to now().text(),
            "connectors" to spot.plugs.map { connector(it.id, it.status, it.type, it.power, spot.id) }, "public" to true, "latitude" to spot.latitude, "longitude" to spot.longitude,
            "maximum_power_kw" to (spot.plugs.maxOfOrNull { it.power } ?: 22.0), "auto_charge" to (spot.id == "PAR-02"),
            "site_id" to "c0000000-0000-4000-8000-000000000002", "site_area_id" to "d0000000-0000-4000-8000-000000000002",
            "site_name" to spot.site, "address" to spot.address, "postal_code" to spot.postal, "city" to "Paris", "country" to "FR",
            "public_evses" to if (spot.evse) listOf(mapOf("id" to DemoSupport.EVSE_ID, "connector_id" to 1, "workspace_slug" to "demo", "name" to spot.name)) else emptyList(),
            "photo_count" to photoCount, "tariff" to tariff,
        )

    private fun stations() = spots.map { station(it, null, null) }

    private fun detail(id: String): Triple<Int, ByteArray, String> {
        val spot = spots.firstOrNull { it.id == id } ?: return problem(404, "Borne introuvable.")
        val tariff = mapOf("name" to "Tarif réseau", "currency" to "EUR", "flat_price" to 0.5, "energy_price" to 0.39, "time_price" to 0.6, "parking_price" to 6)
        return json(station(spot, photos.count { it.station == id }, tariff))
    }

    private fun photoJson(photo: Photo) =
        mapOf(
            "id" to photo.id, "station_id" to photo.station, "content_type" to "image/jpeg", "created_at" to photo.createdAt.text(),
            "mine" to photo.mine, "path" to "/api/v1/account/stations/${photo.station}/photos/${photo.id}",
        )

    private fun stopLive() {
        live?.let { finished.add(0, sessionJson(it, now())) }
        live = null
    }

    private fun sessionJson(current: Live, stoppedAt: Instant? = null): Map<String, Any?> {
        val energy = liveEnergy()
        return mapOf(
            "id" to current.id, "station_id" to current.station, "connector_id" to current.connector, "started_at" to current.startedAt.minusSeconds(1140).text(),
            "stopped_at" to stoppedAt?.text(), "energy_kwh" to energy, "authorization_status" to "Accepted", "stop_reason" to if (stoppedAt == null) null else "Remote",
            "token_uid" to current.token, "user_id" to DemoSupport.USER_ID, "user_name" to "Thomas Bernard", "site_area_id" to null, "site_id" to null,
            "duration_seconds" to (now().epochSecond - current.startedAt.epochSecond + 1140).toInt(), "inactivity_seconds" to 0, "current_power_kw" to if (stoppedAt == null) livePower() else null,
            "state_of_charge_start" to 42, "state_of_charge" to liveSoc(), "price" to (energy * 0.39 + 0.5).places(2), "currency" to "EUR", "pricing_definition_id" to null,
            "billing_status" to if (stoppedAt == null) "NONE" else "PENDING", "invoice_id" to null, "roaming_partner" to null, "public_payment" to false, "soft_stopped" to false,
            "vehicle_id" to defaultCar, "auto_charge" to false, "vid" to null, "errors" to null, "signed_meter_data" to false, "signed_meter_format" to null,
        )
    }

    private fun history(index: Int): Map<String, Any?> {
        val start = now().minusSeconds((index + 1) * 201_600L)
        val names = listOf("PAR-01", "PAR-02", "PAR-06", "PAR-07")
        val energy = listOf(28.4, 41.2, 18.6, 33.9, 12.3, 47.5, 22.8, 36.1, 15.2)[index]
        val seconds = (energy * 330).toInt()
        return mapOf(
            "id" to 2140 - index, "station_id" to names[index % 4], "connector_id" to 1, "started_at" to start.text(), "stopped_at" to start.plusSeconds(seconds.toLong()).text(),
            "energy_kwh" to energy, "authorization_status" to "Accepted", "stop_reason" to "EVDisconnected", "token_uid" to DemoSupport.OWN_CARD, "user_id" to DemoSupport.USER_ID, "user_name" to "Thomas Bernard",
            "site_area_id" to null, "site_id" to null, "duration_seconds" to seconds, "inactivity_seconds" to index * 120, "current_power_kw" to null,
            "state_of_charge_start" to 24 + index, "state_of_charge" to min(100, 24 + index + (energy / 60 * 100).toInt()), "price" to (energy * 0.39 + 0.5).places(2), "currency" to "EUR",
            "pricing_definition_id" to null, "billing_status" to if (index < 2) "INVOICED" else "PAID", "invoice_id" to null, "roaming_partner" to null, "public_payment" to false,
            "soft_stopped" to false, "vehicle_id" to carId, "auto_charge" to false, "vid" to null, "errors" to null, "signed_meter_data" to (index == 0), "signed_meter_format" to if (index == 0) "OCMF" else null,
        )
    }

    private fun sessionsPage(status: String?, station: String?, offset: Int, limit: Int): Map<String, Any?> {
        val rows = mutableListOf<Map<String, Any?>>()
        live?.let { if (status != "completed" && (station == null || station == it.station)) rows += sessionJson(it) }
        if (status != "active") rows += finished.filter { station == null || it["station_id"] == station }
        return mapOf("items" to rows.drop(offset).take(limit), "total" to rows.size)
    }

    private fun session(id: Int): Triple<Int, ByteArray, String> {
        live?.let { if (it.id == id) return json(sessionJson(it)) }
        finished.firstOrNull { it["id"] == id }?.let { return json(it + ("errors" to emptyList<String>())) }
        return problem(404, "Session introuvable.")
    }

    private fun consumptions(id: Int): List<Map<String, Any?>> {
        val current = live?.takeIf { it.id == id }
        val samples = 30
        val step = if (current != null) maxOf(60L, (now().epochSecond - current.startedAt.epochSecond + 1140) / samples) else 300L
        val start = if (current != null) now().minusSeconds(step * samples) else now().minusSeconds(90_000)
        return (0 until samples).map { index ->
            val progress = index.toDouble() / samples
            val power = min(10.8, index * 2.8) * (if (progress > 0.82 && current == null) 0.55 else 1.0) + sin(index.toDouble()) * 0.3
            mapOf(
                "at" to start.plusSeconds(step * index).text(), "energy_kwh" to (index * 0.9).places(2),
                "power_kw" to maxOf(0.0, power).places(2), "soc" to 42 + index / 2, "amperage_a" to 16, "voltage_v" to 230, "cumulated_price" to (index * 0.35).places(2),
            )
        }
    }

    private fun statistics(metric: String, year: Int): Map<String, Any?> {
        val base = listOf(62.0, 48.0, 71.0, 55.0, 83.0, 90.0, 76.0, 41.0, 95.0, 88.0, 64.0, 0.0)
        val (scale, unit) =
            when (metric) {
                "usage" -> 0.11 to "h"
                "inactivity" -> 0.02 to "h"
                "sessions" -> 0.08 to "session"
                "pricing" -> 0.39 to "EUR"
                else -> 1.0 to "kWh"
            }
        val months = base.map { (it * scale).places(if (metric == "sessions") 0 else 2) }
        val total = months.sum().places(2)
        return mapOf(
            "metric" to metric, "year" to year, "unit" to unit, "currency" to if (metric == "pricing") "EUR" else null, "months" to months, "total" to total,
            "groups" to
                listOf(
                    mapOf("key" to "PAR-01", "label" to "Gare de Lyon — Borne A", "months" to months.map { (it * 0.6).places(2) }, "total" to (total * 0.6).places(2)),
                    mapOf("key" to "PAR-02", "label" to "Bercy Village — Rapide", "months" to months.map { (it * 0.4).places(2) }, "total" to (total * 0.4).places(2)),
                ),
        )
    }

    private fun catalog(search: String): Map<String, Any?> {
        val rows =
            listOf(
                listOf("Renault", "Megane E-Tech", "EV60", 60, 22, 130), listOf("Peugeot", "e-208", "GT", 51, 11, 100), listOf("Tesla", "Model 3", "Long Range", 75, 11, 250),
                listOf("Volkswagen", "ID.3", "Pro S", 77, 11, 170), listOf("Hyundai", "Kona Electric", "65 kWh", 65, 11, 102), listOf("Citroën", "ë-C4", "54 kWh", 54, 11, 100),
            )
        val items =
            rows.filter { search.isEmpty() || "${it[0]} ${it[1]}".contains(search, ignoreCase = true) }.map { row ->
                mapOf(
                    "id" to uuid(), "name" to "${row[0]} ${row[1]}", "revision" to 0, "updated_at" to "2026-01-01T00:00:00Z",
                    "data" to mapOf("make" to row[0], "model" to row[1], "version" to row[2], "batteryKwh" to row[3], "maxAcPowerKw" to row[4], "maxDcPowerKw" to row[5]),
                )
            }
        return mapOf("items" to items, "total" to items.size)
    }

    private val invoiceIds = List(3) { uuid() }

    private fun invoice(index: Int, number: String, status: String, amount: Int, created: String, sessions: List<Int>) =
        mapOf(
            "id" to invoiceIds[index], "number" to number, "user_id" to DemoSupport.USER_ID, "user_name" to "Thomas Bernard", "status" to status, "amount_minor" to amount, "currency" to "EUR",
            "created_at" to created, "session_ids" to sessions, "hosted_url" to "https://stripe.com", "last_error" to null,
        )

    private fun invoices() =
        listOf(
            invoice(0, "FA-2026-0183", "OPEN", 3214, "2026-11-01T06:00:00Z", listOf(2140, 2139)),
            invoice(1, "FA-2026-0147", "PAID", 5890, "2026-10-01T06:00:00Z", listOf(2120)),
            invoice(2, "FA-2026-0112", "PAID", 4125, "2026-09-01T06:00:00Z", listOf(2101)),
        )

    private val notificationIds = List(4) { uuid() }

    private fun notifications() =
        listOf(
            Triple("END_OF_SESSION", "Votre session de recharge est terminée", 86_400L),
            Triple("END_OF_CHARGE", "La charge de votre véhicule est terminée", 90_000L),
            Triple("SESSION_STARTED", "Votre session de recharge a commencé", 100_000L),
            Triple("INVOICE_AVAILABLE", "Votre facture est disponible", 259_200L),
        ).mapIndexed { index, (type, subject, age) ->
            mapOf(
                "id" to notificationIds[index], "type" to type, "user_id" to DemoSupport.USER_ID, "recipient" to DemoSupport.EMAIL, "subject" to subject,
                "channel" to "EMAIL", "created_at" to now().minusSeconds(age).text(), "sent_at" to now().minusSeconds(age).text(),
            )
        }

    private fun notificationTypes() =
        listOf(
            "SESSION_STARTED" to "Début de session", "END_OF_CHARGE" to "Fin de charge", "END_OF_SESSION" to "Fin de session",
            "ACCOUNT_STATUS_CHANGED" to "Changement d’état du compte", "ACCOUNT_INACTIVITY" to "Compte inactif",
            "PREPARING_SESSION_NOT_STARTED" to "Véhicule branché sans session", "SESSION_NOT_STARTED_AFTER_AUTHORIZE" to "Badge présenté sans session",
            "INVOICE_AVAILABLE" to "Facture disponible", "BILLING_FAILED" to "Échec de paiement",
        ).map { (type, label) -> mapOf("type" to type, "label" to label, "audience" to "DRIVER") }

    @Suppress("UNCHECKED_CAST")
    private fun normalizedPreferences(): Map<String, Any?> {
        val types = notificationTypes().associate { it["type"].toString() to true } + (preferences["types"] as Map<String, Boolean>)
        return mapOf("email" to preferences["email"], "push" to preferences["push"], "types" to types)
    }

    private fun portal(): Map<String, Any?> {
        val busy = live?.station == "PAR-01"
        return mapOf(
            "id" to DemoSupport.EVSE_ID, "workspace" to "Réseau Démo", "workspaceSlug" to "demo", "name" to "Gare de Lyon — Borne A", "stationId" to "PAR-01", "connectorId" to 1,
            "connected" to true, "status" to if (busy) "Charging" else "Available", "available" to !busy,
            "tariff" to mapOf("currency" to "EUR", "energyPrice" to 0.39, "timePrice" to 0.6, "flatPrice" to 0.5), "minimumCreditMinor" to 500, "maximumCreditMinor" to 10000,
        )
    }

    private fun guestJson(guest: Guest): Map<String, Any?> {
        val age = now().epochSecond - guest.createdAt.epochSecond
        val state =
            when {
                guest.stoppedAt != null -> "COMPLETED"
                age < 4 -> "START_REQUESTED"
                else -> "CHARGING"
            }
        val billed = if (guest.stoppedAt != null) min(guest.credit, 742) else null
        return mapOf(
            "id" to guest.id, "state" to state, "prepaidMinor" to guest.credit, "billedMinor" to billed, "refundedMinor" to (billed?.let { guest.credit - it } ?: 0), "currency" to "EUR",
            "transactionId" to if (state == "START_REQUESTED") null else 2200, "message" to null,
        )
    }
}
