// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.models.*
import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractDecodingTests {
    private fun text(name: String) = File("src/test/resources/fixtures/$name.json").readText()

    private fun <T> fixture(name: String, strategy: KSerializer<T>): T = JsonCoding.decode(strategy, text(name))

    private val fixtures: Map<String, KSerializer<*>> =
        mapOf(
            "configuration" to MobileConfigurationResponse.serializer(),
            "csrf" to CsrfResponse.serializer(),
            "signin" to AppSessionResponse.serializer(),
            "eula" to EulaResponse.serializer(),
            "workspace" to WorkspaceResponse.serializer(),
            "account" to UserResponse.serializer(),
            "sites" to ListSerializer(UserSiteResponse.serializer()),
            "stations" to ListSerializer(CustomerStationResponse.serializer()),
            "public_stations" to ListSerializer(CustomerStationResponse.serializer()),
            "station" to CustomerStationResponse.serializer(),
            "photos" to ListSerializer(StationPhotoResponse.serializer()),
            "tokens" to ListSerializer(ChargingTokenResponse.serializer()),
            "card_offer" to CardOfferResponse.serializer(),
            "card_orders" to ListSerializer(CardOrderResponse.serializer()),
            "card_activated" to ChargingTokenResponse.serializer(),
            "cars" to ListSerializer(RecordResponse.serializer(CarData.serializer())),
            "catalog" to PageResponse.serializer(RecordResponse.serializer(CarCatalogData.serializer())),
            "autocharge" to ListSerializer(AutoChargeCandidate.serializer()),
            "sessions" to PageResponse.serializer(ChargingSessionResponse.serializer()),
            "session" to ChargingSessionResponse.serializer(),
            "consumptions" to ListSerializer(ConsumptionPoint.serializer()),
            "receipt" to CommandReceipt.serializer(),
            "statistics" to StatisticsResponse.serializer(),
            "notifications" to PageResponse.serializer(NotificationResponse.serializer()),
            "types" to ListSerializer(NotificationTypeInfo.serializer()),
            "preferences" to NotificationPreferences.serializer(),
            "invoices" to PageResponse.serializer(InvoiceResponse.serializer()),
            "portal" to PortalEvseResponse.serializer(),
            "guest" to PortalPaymentResponse.serializer(),
        )

    @Test
    fun everyRecordedServerResponseIsCovered() {
        val available = File("src/test/resources/fixtures").list().orEmpty().map { it.removeSuffix(".json") }.toSet()
        assertEquals(available, fixtures.keys)
    }

    @Test
    fun decodesRecordedServerResponses() {
        fixtures.forEach { (name, strategy) -> assertNotNull(name, fixture(name, strategy)) }
    }

    @Test
    fun decodesStationFields() {
        val station = fixture("station", CustomerStationResponse.serializer())
        assertTrue(station.id.isNotEmpty())
        assertNotNull(station.connectors.firstOrNull()?.status)
    }

    @Test
    fun keepsNotificationTypeKeys() {
        val preferences =
            JsonCoding.decode(
                NotificationPreferences.serializer(),
                """{"email":true,"push":false,"types":{"SESSION_STARTED":true,"END_OF_CHARGE":false}}""",
            )
        assertEquals(false, preferences.types["END_OF_CHARGE"])
        assertEquals(true, preferences.types["SESSION_STARTED"])
    }

    @Test
    fun decodesServerInstantsWithAnyPrecision() {
        val decode = { value: String -> JsonCoding.decode(GoogleOrInstant.serializer(), """{"at":"$value"}""").at }
        assertNotNull(decode("2026-11-04T08:55:28.536984Z"))
        assertNotNull(decode("2026-11-04T08:55:28Z"))
        assertNotNull(decode("2026-11-04T08:55:28.5Z"))
    }

    @Test
    fun encodesInputsInServerNaming() {
        val input = JsonCoding.json.encodeToString(SessionStartInput.serializer(), SessionStartInput("A", 1, "B"))
        val keys = JsonCoding.json.parseToJsonElement(input).jsonObject.keys
        assertEquals(setOf("stationId", "connectorId", "tokenUid"), keys)
        val record =
            JsonCoding.json.encodeToString(
                RecordInput.serializer(CarData.serializer()),
                RecordInput("x", CarData(licensePlate = "AA-123-BB", batteryKwh = 60.0)),
            )
        val parsed = JsonCoding.json.parseToJsonElement(record).jsonObject
        assertFalse("revision" in parsed)
        val data = parsed.getValue("data") as JsonObject
        assertTrue("licensePlate" in data)
        assertFalse("vin" in data)
    }

    @Test
    fun readsTheGoogleWalletPassResponse() {
        val pass = JsonCoding.decode(GoogleWalletPassResponse.serializer(), """{"jwt":"a.b.c","save_url":"https://pay.google.com/gp/v/save/a.b.c"}""")
        assertEquals("a.b.c", pass.jwt)
        assertTrue(pass.saveUrl.endsWith("a.b.c"))
    }

    @kotlinx.serialization.Serializable
    private data class GoogleOrInstant(@kotlinx.serialization.Serializable(with = InstantSerializer::class) val at: java.time.Instant)
}
