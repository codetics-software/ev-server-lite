// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import android.graphics.Bitmap
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.features.sessions.SessionDetailScreen
import fr.codetics.evapp.features.sessions.SessionsScreen
import fr.codetics.evapp.features.stations.PhotoEncoder
import fr.codetics.evapp.features.stations.ReportProblemScreen
import fr.codetics.evapp.features.stations.StationDetailScreen
import fr.codetics.evapp.models.ChargingSessionResponse
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAndStationScreensTests : ScreenTest() {
    private fun session(json: String = FixtureServer.fixture("session")) = JsonCoding.decode(ChargingSessionResponse.serializer(), json)

    private val finished =
        FixtureServer.fixture("session")
            .replace("\"stopped_at\":null", "\"stopped_at\":\"2026-11-04T10:17:07Z\"")
            .replace("\"signed_meter_data\":false", "\"signed_meter_data\":true")
            .replace("\"state_of_charge\":54", "\"state_of_charge\":80")
            .replace("\"billing_status\":\"NONE\"", "\"billing_status\":\"PAID\"")
            .replace("\"roaming_partner\":null", "\"roaming_partner\":\"Gireve\"")

    @Test
    fun historyListsSessionsAndOpensTheirDetail() {
        connect()
        show { SessionsScreen() }
        awaitTag("session.row.1")
        assertTrue(hasText("Borne Contrat"))
        assertTrue(hasText(string(R.string.in_progress)))
        clickTag("session.row.1")
        assertEquals(1, (top() as Route.SessionDetail).session.id)
    }

    @Test
    fun anEmptyHistoryInvitesToCharge() {
        fixtures.respond("GET", "transactions", """{"items":[],"total":0}""")
        connect()
        show { SessionsScreen() }
        awaitText(string(R.string.no_sessions_yet))
    }

    @Test
    fun historyPagesAreLoadedOnScroll() {
        val items = (1..30).joinToString(",") { FixtureServer.fixture("session").replace("\"id\":1,", "\"id\":$it,").replace("\"stopped_at\":null", "\"stopped_at\":\"2026-11-04T10:17:07Z\"") }
        fixtures.respond("GET", "transactions", """{"items":[$items],"total":31}""")
        connect()
        show { SessionsScreen() }
        awaitTag("session.row.1")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(29)
        await { fixtures.calls("GET", "transactions").any { it.query?.contains("offset=30") == true } }
    }

    @Test
    fun historyErrorsAreShown() {
        connect()
        fixtures.fail("GET", "transactions", 500, "Historique indisponible")
        show { SessionsScreen() }
        awaitText("Historique indisponible")
    }

    @Test
    fun statisticsShowTheTotalAndCanChangeMetricAndYear() {
        connect()
        show { SessionsScreen() }
        clickTag("sessions.statistics")
        awaitText("8 kWh")
        awaitTag("stats.chart")
        clickTag("stats.metric")
        compose.onAllNodesWithText(string(R.string.sessions)).onLast().performClick()
        await { fixtures.calls("GET", "statistics/sessions").isNotEmpty() }
        awaitText("8")
        clickTag("stats.year")
        click("2025")
        await { fixtures.calls("GET", "statistics/sessions").any { it.query == "year=2025" } }
        clickTag("sessions.history")
        awaitTag("session.row.1")
    }

    @Test
    fun statisticsListEveryStationWhenThereAreSeveral() {
        fixtures.respond(
            "GET",
            "statistics/pricing",
            """{"metric":"pricing","year":2026,"unit":"EUR","currency":"EUR","groups":[{"key":"A","label":"Borne A","months":[1.5],"total":1.5},{"key":"B","label":"Borne B","months":[2.0],"total":2.0}],"months":[3.5],"total":3.5}""",
        )
        connect()
        show { SessionsScreen() }
        clickTag("sessions.statistics")
        clickTag("stats.metric")
        click(string(R.string.spending))
        awaitText("€3.50")
        assertTrue(hasText(string(R.string.by_station)))
        assertTrue(hasText("Borne B"))
        assertTrue(hasText("€2.00"))
    }

    @Test
    fun theDetailOfAFinishedSessionShowsSignedReadings() {
        connect()
        fixtures.respond("GET", "transactions/1", finished)
        show { SessionDetailScreen(session()) }
        awaitText(string(R.string.signed_meter_data))
        assertTrue(hasText("54% → 80%"))
        assertTrue(hasText(string(R.string.paid)))
        assertTrue(hasText("Gireve"))
        assertTrue(hasText("OCMF"))
        assertTrue(hasText("04A1B2C3D4E580"))
        assertFalse(hasText(string(R.string.follow_live)))
        click(string(R.string.start_reading))
        awaitText("OCMF|start")
        click(string(R.string.report_a_problem))
        assertEquals(Route.Report("CONTRAT-1", 1, string(R.string.session_x, "1")), top())
    }

    @Test
    fun anActiveSessionCanBeFollowedLive() {
        connect()
        show { SessionDetailScreen(session()) }
        awaitText(string(R.string.follow_live))
        assertTrue(hasText(string(R.string.not_billed)))
        click(string(R.string.follow_live))
        assertEquals(Route.LiveSession(1), top())
    }

    @Test
    fun aStationShowsItsConnectorsAndTheDriversSession() {
        connect()
        show { StationDetailScreen("CONTRAT-1") }
        awaitText("Gare")
        assertTrue(hasText("1 place de la Gare, 69002 Lyon"))
        assertTrue(hasText("Codetics Lite"))
        assertTrue(hasText(string(R.string.x_no_x, string(R.string.connector), "1")))
        assertTrue(hasText("FR*COD*ECONTRAT11"))
        assertTrue(hasText(string(R.string.choose_from_library)))
        click(string(R.string.open_my_session))
        assertEquals(AppTab.Charge, model.selectedTab)
        assertEquals(Route.LiveSession(5), top(AppTab.Charge))
        model.selectedTab = AppTab.Map
        click(string(R.string.report_a_problem))
        assertEquals(Route.Report("CONTRAT-1"), top(AppTab.Map))
    }

    @Test
    fun aFreeConnectorCanBeStartedAndTariffsAreListed() {
        val free =
            FixtureServer.fixture("station")
                .replace("\"current_transaction_id\":5", "\"current_transaction_id\":null")
                .replace("\"auto_charge\":false", "\"auto_charge\":true")
                .replace(Regex("\"tariff\":\\{[^}]*\\}"), "\"tariff\":{\"name\":\"Tarif public\",\"currency\":\"EUR\",\"energy_price\":0.35,\"time_price\":0,\"parking_price\":2.5,\"flat_price\":1}")
        fixtures.respond("GET", "account/stations/CONTRAT-1", free)
        connect()
        show { StationDetailScreen("CONTRAT-1") }
        awaitText("Tarif public")
        assertTrue(hasText("€0.35 / kWh"))
        assertTrue(hasText("€2.50 / h"))
        assertTrue(hasText("€1.00"))
        assertFalse(hasText(string(R.string.charging_time)))
        assertTrue(hasText(string(R.string.automatic_charging_when_you_plug_in)))
        clickTag("connector.start.1")
        assertEquals(Route.StartCharge(fr.codetics.evapp.app.StartRequest("CONTRAT-1", "Borne Contrat", 1)), top())
        startedIntents()
        clickTag("station.directions")
        assertTrue(startedIntents().any { it.data?.scheme == "geo" })
    }

    @Test
    fun guestsAreInvitedToSignInToCharge() {
        connect(signedIn = false)
        fixtures.respond("GET", "public/workspaces/${FixtureServer.WORKSPACE}/stations/CONTRAT-1", FixtureServer.fixture("station").replace("\"current_transaction_id\":5", "\"current_transaction_id\":null"))
        show { StationDetailScreen("CONTRAT-1") }
        awaitText(string(R.string.to_start_a_charge_sign_in_or_scan_the_qr_code_on))
        assertFalse(hasTag("connector.start.1"))
        assertFalse(hasText(string(R.string.photos)))
    }

    @Test
    fun anUnknownStationShowsTheError() {
        connect()
        fixtures.fail("GET", "account/stations/NOPE", 404, "Borne introuvable.")
        show { StationDetailScreen("NOPE") }
        awaitText("Borne introuvable.")
    }

    @Test
    fun aStationWithoutConnectorsSaysSo() {
        connect()
        fixtures.respond("GET", "account/stations/CONTRAT-1", FixtureServer.fixture("station").replace(Regex("\"connectors\":\\[.*?\\],\"public\""), "\"connectors\":[],\"public\""))
        show { StationDetailScreen("CONTRAT-1") }
        awaitText(string(R.string.this_station_has_not_declared_any_connector_yet))
    }

    @Test
    fun theDemoPhotoIsUploadedViewedAndDeleted() {
        model.startDemo(signIn = true)
        await { model.launch == Launch.Ready && model.isSignedIn && model.stations.stations.isNotEmpty() }
        show { StationDetailScreen("PAR-01") }
        val before = photoCount()
        clickTag("photo.demo")
        await { photoCount() == before + 1 }
        assertFalse(hasText(string(R.string.this_image_could_not_be_prepared)))
    }

    private fun photoCount(): Int {
        var count = 0
        runOnModel { count = model.api?.photos("PAR-01")?.size ?: 0 }
        return count
    }

    @Test
    fun reportingAProblemSendsItToTheOperator() {
        connect()
        show { ReportProblemScreen("CONTRAT-1", 1, "Câble abîmé") }
        assertTrue(hasText(string(R.string.station_x, "CONTRAT-1")))
        inputTag("report.details", "Le câble est coupé")
        input(string(R.string.phone_optional), "+33 6 00 00 00 00")
        clickTag("report.send")
        awaitText(string(R.string.thank_you_the_operator_has_been_told_about_this))
        val body = fixtures.calls("POST", "notifications/report-error").single().body
        assertTrue(body.contains("\"subject\":\"Câble abîmé\""))
        assertTrue(body.contains("\"connectorId\":1"))
        assertTrue(body.contains("\"phone\":\"+33 6 00 00 00 00\""))
    }

    @Test
    fun reportFailuresAreShown() {
        connect()
        fixtures.fail("POST", "notifications/report-error", 500, "Envoi impossible")
        show { ReportProblemScreen(null, null, "") }
        inputTag("report.subject", "Appli")
        inputTag("report.details", "Elle plante")
        clickTag("report.send")
        awaitText("Envoi impossible")
    }

    @Test
    fun photosAreScaledAndCompressedBeforeUpload() {
        val large = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888)
        assertNotNull(PhotoEncoder.jpeg(large))
        assertNull(PhotoEncoder.read(app, android.net.Uri.parse("content://missing/photo.jpg")))
    }
}
