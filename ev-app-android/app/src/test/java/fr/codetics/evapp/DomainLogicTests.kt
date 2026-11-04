// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import fr.codetics.evapp.app.BadgeMatcher
import fr.codetics.evapp.app.ConnectorStatus
import fr.codetics.evapp.app.Coordinate
import fr.codetics.evapp.app.PortalLink
import fr.codetics.evapp.app.QrCodeParser
import fr.codetics.evapp.app.StationAvailability
import fr.codetics.evapp.app.StationFilter
import fr.codetics.evapp.app.distanceMeters
import fr.codetics.evapp.core.ConfigurationIssue
import fr.codetics.evapp.core.ConfigurationOutcome
import fr.codetics.evapp.core.ConfigurationValidator
import fr.codetics.evapp.models.ChargingTokenResponse
import fr.codetics.evapp.models.ConnectorResponse
import fr.codetics.evapp.models.CustomerStationResponse
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationValidatorTests {
    private val valid =
        mapOf(
            "API_URL" to "https://supervision.reseau.fr",
            "PORTAL_URL" to "https://supervision.reseau.fr",
            "WORKSPACE_SLUG" to "reseau",
            "BRAND_NAME" to "Mon Réseau",
            "SUPPORT_EMAIL" to "support@reseau.fr",
            "PRIVACY_POLICY_URL" to "https://reseau.fr/confidentialite",
            "MAP_STYLE_URL" to "https://tiles.openfreemap.org/styles/liberty",
            "FIREBASE_PROJECT_ID" to "ev-reseau",
            "FIREBASE_APPLICATION_ID" to "1:123:android:abc",
            "FIREBASE_API_KEY" to "AIzaKey",
            "FIREBASE_SENDER_ID" to "123",
        )

    private fun issues(values: Map<String, String>, local: Boolean = false) =
        (ConfigurationValidator.validate(values, local) as? ConfigurationOutcome.Invalid)?.issues.orEmpty()

    @Test
    fun acceptsACompleteConfiguration() {
        val outcome = ConfigurationValidator.validate(valid, false)
        assertTrue(outcome is ConfigurationOutcome.Valid)
        assertEquals("ev-reseau", (outcome as ConfigurationOutcome.Valid).configuration.firebase?.projectId)
    }

    @Test
    fun rejectsEveryPlaceholderInTheShippedTemplate() {
        val template =
            java.util.Properties().apply {
                java.io.File("src/main/assets/AppConfiguration.properties").inputStream().use(::load)
            }
        val values = template.stringPropertyNames().associateWith(template::getProperty)
        val found = issues(values).filterIsInstance<ConfigurationIssue.Placeholder>().map { it.key }.toSet()
        assertEquals(
            setOf(
                "API_URL", "PORTAL_URL", "WORKSPACE_SLUG", "BRAND_NAME", "SUPPORT_EMAIL", "PRIVACY_POLICY_URL", "GOOGLE_MAPS_API_KEY",
                "FIREBASE_PROJECT_ID", "FIREBASE_APPLICATION_ID", "FIREBASE_API_KEY", "FIREBASE_SENDER_ID",
            ),
            found,
        )
    }

    @Test
    fun refusesCleartextOutsideDebugLocalhost() {
        val cleartext = valid + ("API_URL" to "http://10.0.2.2:8080")
        assertEquals(listOf<ConfigurationIssue>(ConfigurationIssue.InsecureUrl("API_URL")), issues(cleartext))
        assertTrue(issues(cleartext, local = true).isEmpty())
        assertEquals(listOf<ConfigurationIssue>(ConfigurationIssue.InsecureUrl("API_URL")), issues(valid + ("API_URL" to "http://reseau.fr"), local = true))
    }

    @Test
    fun rejectsReservedDomainsBadSlugsAndEmails() {
        assertEquals(listOf<ConfigurationIssue>(ConfigurationIssue.InvalidValue("API_URL")), issues(valid + ("API_URL" to "https://demo.example.com")))
        assertEquals(listOf<ConfigurationIssue>(ConfigurationIssue.InvalidValue("WORKSPACE_SLUG")), issues(valid + ("WORKSPACE_SLUG" to "Mon Espace")))
        assertEquals(listOf<ConfigurationIssue>(ConfigurationIssue.InvalidValue("SUPPORT_EMAIL")), issues(valid + ("SUPPORT_EMAIL" to "nope")))
    }

    @Test
    fun googleMapsKeyIsOptionalAndSelectsTheProvider() {
        val without = ConfigurationValidator.validate(valid, false) as ConfigurationOutcome.Valid
        assertNull(without.configuration.googleMapsApiKey)
        val with = ConfigurationValidator.validate(valid + ("GOOGLE_MAPS_API_KEY" to "AIzaKey"), false) as ConfigurationOutcome.Valid
        assertEquals("AIzaKey", with.configuration.googleMapsApiKey)
        assertEquals(
            listOf<ConfigurationIssue>(ConfigurationIssue.Placeholder("GOOGLE_MAPS_API_KEY")),
            issues(valid + ("GOOGLE_MAPS_API_KEY" to "__GOOGLE_MAPS_API_KEY__")),
        )
    }

    @Test
    fun firebaseIsOptionalButMustBeComplete() {
        val without = valid.filterKeys { !it.startsWith("FIREBASE") }
        val outcome = ConfigurationValidator.validate(without, false) as ConfigurationOutcome.Valid
        assertNull(outcome.configuration.firebase)
        assertEquals(
            listOf<ConfigurationIssue>(ConfigurationIssue.InvalidValue("FIREBASE_SENDER_ID")),
            issues(valid - "FIREBASE_SENDER_ID"),
        )
    }
}

class QrCodeParserTests {
    private val portal = "https://supervision.reseau.fr"
    private val evse = "5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10"

    @Test
    fun acceptsAChargingPointOfThisNetwork() {
        assertEquals(
            QrCodeParser.Outcome.Success(PortalLink("reseau", UUID.fromString(evse))),
            QrCodeParser.parse("  https://supervision.reseau.fr/charge/reseau/$evse\n", portal),
        )
    }

    @Test
    fun refusesForeignNetworksAndOtherPaths() {
        assertEquals(QrCodeParser.Outcome.Rejected(QrCodeParser.Failure.ForeignNetwork), QrCodeParser.parse("https://evil.example/charge/reseau/$evse", portal))
        assertEquals(QrCodeParser.Outcome.Rejected(QrCodeParser.Failure.ForeignNetwork), QrCodeParser.parse("http://supervision.reseau.fr/charge/reseau/$evse", portal))
        assertEquals(QrCodeParser.Outcome.Rejected(QrCodeParser.Failure.NotAChargingPoint), QrCodeParser.parse("https://supervision.reseau.fr/charge/reseau/nope", portal))
        assertEquals(QrCodeParser.Outcome.Rejected(QrCodeParser.Failure.NotALink), QrCodeParser.parse("BEGIN:VCARD", portal))
    }
}

class BadgeMatcherTests {
    private fun token(uid: String, enabled: Boolean = true, expiresAt: Instant? = null) =
        ChargingTokenResponse(uid = uid, type = "RFID", enabled = enabled, default = false, expiresAt = expiresAt)

    @Test
    fun matchesDirectAndReversedIdentifiers() {
        val own = token("04:A1:B2:C3")
        assertEquals(BadgeMatcher.Outcome.Registered(own), BadgeMatcher.match("04a1b2c3", listOf(own)))
        assertEquals(BadgeMatcher.Outcome.Registered(own), BadgeMatcher.match("C3B2A104", listOf(own)))
    }

    @Test
    fun reportsDisabledExpiredAndUnknownCards() {
        val now = Instant.parse("2026-11-04T10:00:00Z")
        val disabled = token("AA", enabled = false)
        val expired = token("BB", expiresAt = now.minusSeconds(1))
        assertEquals(BadgeMatcher.Outcome.Disabled(disabled), BadgeMatcher.match("AA", listOf(disabled), now))
        assertEquals(BadgeMatcher.Outcome.Expired(expired), BadgeMatcher.match("BB", listOf(expired), now))
        assertEquals(BadgeMatcher.Outcome.NotRegistered("CC"), BadgeMatcher.match("cc", listOf(disabled), now))
    }
}

class StationFilterTests {
    private fun connector(status: String, type: String = "T2", power: Double = 22.0) =
        ConnectorResponse(connectorId = 1, status = status, errorCode = "NoError", statusAt = Instant.EPOCH, type = type, maximumPowerKw = power)

    private fun station(id: String, latitude: Double, connectors: List<ConnectorResponse>, connected: Boolean = true) =
        CustomerStationResponse(
            id = id, name = "Borne $id", connected = connected, connectors = connectors, public = true,
            latitude = latitude, longitude = 2.35, autoCharge = false, publicEvses = emptyList(), city = "Paris",
        )

    private val near = station("A", 48.85, listOf(connector("Charging")))
    private val far = station("B", 49.50, listOf(connector("Available", "CCS", 150.0)))

    @Test
    fun sortsByDistance() {
        val sorted = StationFilter().apply(listOf(far, near), "", Coordinate(48.85, 2.35))
        assertEquals(listOf("A", "B"), sorted.map { it.id })
    }

    @Test
    fun filtersByAvailabilityConnectorPowerAndSearch() {
        val stations = listOf(near, far)
        assertEquals(listOf("B"), StationFilter(onlyAvailable = true).apply(stations, "", null).map { it.id })
        assertEquals(listOf("B"), StationFilter(connectorType = "CCS").apply(stations, "", null).map { it.id })
        assertEquals(listOf("B"), StationFilter(minimumPowerKw = 50.0).apply(stations, "", null).map { it.id })
        assertEquals(listOf("A"), StationFilter().apply(stations, "borne a", null).map { it.id })
    }

    @Test
    fun derivesAvailabilityLikeTheServerStatuses() {
        assertTrue(StationAvailability.of(far).isAvailable)
        assertEquals(StationAvailability.Busy, StationAvailability.of(near))
        assertEquals(StationAvailability.OutOfOrder, StationAvailability.of(station("C", 48.0, listOf(connector("Faulted")))))
        assertEquals(StationAvailability.Offline, StationAvailability.of(station("D", 48.0, listOf(connector("Available")), connected = false)))
        assertEquals(ConnectorStatus.PluggedIn, ConnectorStatus.of("Preparing"))
        assertTrue(ConnectorStatus.of("Preparing").isStartable)
        assertFalse(ConnectorStatus.of("Charging").isStartable)
    }

    @Test
    fun distanceIsAccurateEnough() {
        val paris = Coordinate(48.8566, 2.3522)
        val lyon = Coordinate(45.7640, 4.8357)
        assertEquals(392_000.0, distanceMeters(paris, lyon), 3_000.0)
    }
}
