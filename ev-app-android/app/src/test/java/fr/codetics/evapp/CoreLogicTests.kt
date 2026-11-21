// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import fr.codetics.evapp.app.ConnectorStatus
import fr.codetics.evapp.app.ConnectorType
import fr.codetics.evapp.app.StationAvailability
import fr.codetics.evapp.app.addressLine
import fr.codetics.evapp.app.highestPowerKw
import fr.codetics.evapp.core.ConfigurationIssue
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.models.*
import fr.codetics.evapp.support.FixtureServer
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoreLogicTests {
    private lateinit var locale: Locale
    private lateinit var zone: TimeZone

    @Before
    fun pinLocale() {
        locale = Locale.getDefault()
        zone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(locale)
        TimeZone.setDefault(zone)
    }

    @Test
    fun formatsPricesWithUpToFourDecimals() {
        assertEquals("—", Formatters.price(null, "EUR"))
        assertEquals("€1.50", Formatters.price(BigDecimal("1.5"), "EUR"))
        assertEquals("€0.3912", Formatters.price(BigDecimal("0.3912"), "EUR"))
        assertEquals("€2.00", Formatters.price(BigDecimal("2"), null))
        assertEquals("$3.25", Formatters.price(BigDecimal("3.25"), "USD"))
    }

    @Test
    fun formatsMinorUnits() {
        assertEquals("€15.00", Formatters.minor(1500, "EUR"))
        assertEquals("€0.05", Formatters.minor(5, "EUR"))
        assertEquals("—", Formatters.minor(null, "EUR"))
    }

    @Test
    fun formatsEnergyPowerDurationAndDistance() {
        assertEquals("3.46 kWh", Formatters.energy(3.456))
        assertEquals("—", Formatters.energy(null))
        assertEquals("11 kW", Formatters.power(11.0))
        assertEquals("22.3 kW", Formatters.power(22.26))
        assertEquals("—", Formatters.power(null))
        assertEquals("0 min", Formatters.duration(59))
        assertEquals("2 min", Formatters.duration(125))
        assertEquals("1 h 02 min", Formatters.duration(3725))
        assertEquals("950 m", Formatters.distance(950.7))
        assertEquals("1.5 km", Formatters.distance(1520.0))
    }

    @Test
    fun formatsDatesInTheDeviceZone() {
        val instant = Instant.parse("2026-11-04T09:17:07Z")
        assertEquals("Nov 4, 2026", Formatters.date(instant))
        val dateTime = Formatters.dateTime(instant)
        assertTrue(dateTime, dateTime.startsWith("Nov 4, 2026"))
        assertTrue(dateTime, dateTime.contains("9:17"))
    }

    @Test
    fun convertsSnakeCaseKeysRecursively() {
        assertEquals("stationId", JsonCoding.camelCase("station_id"))
        assertEquals("maximumPowerKw", JsonCoding.camelCase("maximum_power_kw"))
        assertEquals("headerName", JsonCoding.camelCase("headerName"))
        assertEquals("SESSION_STARTED", JsonCoding.camelCase("SESSION_STARTED"))
        val normalized =
            JsonCoding.normalize(
                JsonCoding.json.parseToJsonElement("""{"outer_key":[{"inner_key":1}],"plain":true}""")
            ).jsonObject
        assertEquals(setOf("outerKey", "plain"), normalized.keys)
        assertTrue(normalized.toString().contains("innerKey"))
    }

    @Test
    fun configurationIssuesCompareByKindAndKey() {
        assertEquals(ConfigurationIssue.Placeholder("API_URL"), ConfigurationIssue.Placeholder("API_URL"))
        assertEquals(ConfigurationIssue.Placeholder("API_URL").hashCode(), ConfigurationIssue.Placeholder("API_URL").hashCode())
        assertNotEquals(ConfigurationIssue.Placeholder("API_URL"), ConfigurationIssue.InvalidValue("API_URL"))
        assertNotEquals(ConfigurationIssue.InsecureUrl("API_URL"), ConfigurationIssue.InsecureUrl("PORTAL_URL"))
        assertEquals("InsecureUrl(API_URL)", ConfigurationIssue.InsecureUrl("API_URL").toString())
        assertEquals("AppConfiguration.properties", ConfigurationIssue.MissingFile.key)
    }

    @Test
    fun mapsEveryOcppStatusToADriverStatus() {
        val expected =
            mapOf(
                "Available" to ConnectorStatus.Available,
                "Preparing" to ConnectorStatus.PluggedIn,
                "Charging" to ConnectorStatus.Charging,
                "SuspendedEV" to ConnectorStatus.Paused,
                "SuspendedEVSE" to ConnectorStatus.Paused,
                "Finishing" to ConnectorStatus.Finishing,
                "Reserved" to ConnectorStatus.Reserved,
                "Unavailable" to ConnectorStatus.Unavailable,
                "Faulted" to ConnectorStatus.Faulted,
                "Occupied" to ConnectorStatus.Unknown,
            )
        expected.forEach { (status, mapped) -> assertEquals(status, mapped, ConnectorStatus.of(status)) }
        assertEquals(setOf(ConnectorStatus.Available, ConnectorStatus.PluggedIn), ConnectorStatus.entries.filter { it.isStartable }.toSet())
    }

    @Test
    fun namesConnectorTypesForDrivers() {
        assertEquals("Type 2", ConnectorType.known("IEC_62196_T2"))
        assertEquals("CCS", ConnectorType.known("CCS2"))
        assertEquals("CHAdeMO", ConnectorType.known("CHADEMO"))
        assertEquals("Type 3", ConnectorType.known("T3"))
        assertEquals("Domestic", ConnectorType.known("E/F"))
        assertEquals("Tesla", ConnectorType.known("Tesla"))
        assertNull(ConnectorType.known(null))
    }

    @Test
    fun describesTheRecordedStation() {
        val station = decode("station", CustomerStationResponse.serializer())
        assertEquals("1 place de la Gare, 69002 Lyon", station.addressLine)
        assertNull(station.highestPowerKw)
        assertTrue(StationAvailability.of(station) is StationAvailability.Free)
        val bare = station.copy(address = null, postalCode = null, city = null, maximumPowerKw = 7.4)
        assertNull(bare.addressLine)
        assertEquals(7.4, bare.highestPowerKw!!, 0.0)
        assertEquals(StationAvailability.Offline, StationAvailability.of(station.copy(connected = false)))
        assertEquals(StationAvailability.Busy, StationAvailability.of(station.copy(connectors = emptyList())))
    }

    @Test
    fun recordedResponsesSurviveAnEncodeDecodeRoundTrip() {
        val serializers: Map<String, KSerializer<*>> =
            mapOf(
                "configuration" to MobileConfigurationResponse.serializer(),
                "signin" to AppSessionResponse.serializer(),
                "workspace" to WorkspaceResponse.serializer(),
                "account" to UserResponse.serializer(),
                "sites" to ListSerializer(UserSiteResponse.serializer()),
                "stations" to ListSerializer(CustomerStationResponse.serializer()),
                "photos" to ListSerializer(StationPhotoResponse.serializer()),
                "tokens" to ListSerializer(ChargingTokenResponse.serializer()),
                "card_offer" to CardOfferResponse.serializer(),
                "card_orders" to ListSerializer(CardOrderResponse.serializer()),
                "cars" to ListSerializer(RecordResponse.serializer(CarData.serializer())),
                "catalog" to PageResponse.serializer(RecordResponse.serializer(CarCatalogData.serializer())),
                "sessions" to PageResponse.serializer(ChargingSessionResponse.serializer()),
                "consumptions" to ListSerializer(ConsumptionPoint.serializer()),
                "receipt" to CommandReceipt.serializer(),
                "statistics" to StatisticsResponse.serializer(),
                "notifications" to PageResponse.serializer(NotificationResponse.serializer()),
                "types" to ListSerializer(NotificationTypeInfo.serializer()),
                "preferences" to NotificationPreferences.serializer(),
                "portal" to PortalEvseResponse.serializer(),
                "guest" to PortalPaymentResponse.serializer(),
                "eula" to EulaResponse.serializer(),
                "csrf" to CsrfResponse.serializer(),
            )
        serializers.forEach { (name, serializer) -> assertRoundTrip(name, serializer) }
    }

    @Test
    fun inputsAndHandBuiltResponsesRoundTrip() {
        val inputs =
            listOf(
                roundTrip(LoginRequest.serializer(), LoginRequest("ws", "a@b.c", "secret")),
                roundTrip(SignupRequest.serializer(), SignupRequest("a@b.c", "Ann", "secret")),
                roundTrip(ChallengeRequest.serializer(), ChallengeRequest("ws", "a@b.c")),
                roundTrip(ProfileInput.serializer(), ProfileInput("Ann", "+33", "fr")),
                roundTrip(ErrorReportInput.serializer(), ErrorReportInput("Subject", "Details", "S1", 2, "+33")),
                roundTrip(PushDeviceInput.serializer(), PushDeviceInput("t", "ANDROID", "sandbox", "fr")),
                roundTrip(CardActivationInput.serializer(), CardActivationInput("CARTE-2")),
                roundTrip(AutoChargeInput.serializer(), AutoChargeInput(7)),
                roundTrip(SavedRecord.serializer(), SavedRecord(java.util.UUID.randomUUID(), 3)),
                roundTrip(RedirectResponse.serializer(), RedirectResponse("https://codetics.fr")),
                roundTrip(MutationResponse.serializer(), MutationResponse(true)),
                roundTrip(ProblemDetail.serializer(), ProblemDetail(404, "Not found", "Missing", "missing")),
                roundTrip(
                    SignedMeterData.serializer(),
                    SignedMeterData(1, "S1", "OCMF", "start", "stop"),
                ),
                roundTrip(
                    CheckoutRequest.serializer(),
                    CheckoutRequest(java.util.UUID.randomUUID(), "token", 2000),
                ),
                roundTrip(
                    CheckoutResponse.serializer(),
                    CheckoutResponse(java.util.UUID.randomUUID(), "https://checkout.stripe.com"),
                ),
                roundTrip(
                    InvoiceResponse.serializer(),
                    InvoiceResponse(java.util.UUID.randomUUID(), "F-1", java.util.UUID.randomUUID(), "Ann", "OPEN", 1250, "EUR", Instant.parse("2026-01-01T00:00:00Z"), listOf(1, 2), "https://pay", null),
                ),
                roundTrip(
                    PaymentMethodResponse.serializer(),
                    PaymentMethodResponse("pm_1", "visa", "4242", 8, 2029, true, Instant.parse("2026-01-01T00:00:00Z")),
                ),
                roundTrip(
                    AutoChargeCandidate.serializer(),
                    AutoChargeCandidate(3, "S1", 1, Instant.parse("2026-01-01T00:00:00Z"), "VID:1", null),
                ),
                roundTrip(
                    VehicleIdResponse.serializer(),
                    VehicleIdResponse("VID:1", java.util.UUID.randomUUID(), null, true, Instant.parse("2026-01-01T00:00:00Z")),
                ),
                roundTrip(GoogleWalletPassResponse.serializer(), GoogleWalletPassResponse("a.b.c", "https://pay.google.com")),
            )
        assertTrue(inputs.all { it })
    }

    @Test
    fun decimalsKeepTheirExactServerValue() {
        val tariff = JsonCoding.decode(ChargingTariff.serializer(), """{"currency":"EUR","energy_price":0.3912,"time_price":1.20,"flat_price":0}""")
        assertEquals(BigDecimal("0.3912"), tariff.energyPrice)
        assertEquals(BigDecimal("1.20"), tariff.timePrice)
        val encoded = JsonCoding.json.encodeToJsonElement(ChargingTariff.serializer(), tariff).jsonObject
        assertEquals(JsonPrimitive(BigDecimal("0.3912")), encoded["energyPrice"])
        assertFalse(PaymentState.Charging.isFinished)
        assertTrue(listOf(PaymentState.Completed, PaymentState.Cancelled, PaymentState.Failed, PaymentState.Disputed).all { it.isFinished })
    }

    @Test
    fun recordInputsDropNullsButKeepRevisions() {
        val element =
            JsonCoding.json.encodeToJsonElement(
                RecordInput.serializer(CarData.serializer()),
                RecordInput("Zoé", CarData(make = "Renault"), 4),
            ).jsonObject
        val expected = buildJsonObject {
            put("name", "Zoé")
            put("data", buildJsonObject { put("make", "Renault") })
            put("revision", 4)
        }
        assertEquals(expected, element)
    }

    private fun <T> decode(name: String, serializer: KSerializer<T>): T =
        JsonCoding.decode(serializer, File("src/test/resources/fixtures/$name.json").readText())

    @Suppress("UNCHECKED_CAST")
    private fun assertRoundTrip(name: String, serializer: KSerializer<*>) {
        val strategy = serializer as KSerializer<Any?>
        val value = JsonCoding.decode(strategy, FixtureServer.fixture(name))
        val again = JsonCoding.decode(strategy, JsonCoding.json.encodeToString(strategy, value))
        assertEquals(name, value, again)
    }

    private fun <T> roundTrip(serializer: KSerializer<T>, value: T): Boolean {
        val decoded = JsonCoding.decode(serializer, JsonCoding.json.encodeToString(serializer, value))
        assertEquals(value, decoded)
        return true
    }
}
