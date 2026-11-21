// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.GuestOrder
import fr.codetics.evapp.app.PortalLink
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.app.StartRequest
import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.features.charging.ChargeScreen
import fr.codetics.evapp.features.charging.ChargingPointIdScreen
import fr.codetics.evapp.features.charging.ChargingPointScreen
import fr.codetics.evapp.features.charging.ConsumptionChart
import fr.codetics.evapp.features.charging.GuestCheckoutScreen
import fr.codetics.evapp.features.charging.GuestSessionScreen
import fr.codetics.evapp.features.charging.LiveSessionScreen
import fr.codetics.evapp.features.charging.QrScannerScreen
import fr.codetics.evapp.features.charging.StartChargeScreen
import fr.codetics.evapp.features.charging.stopReasonLabel
import fr.codetics.evapp.models.ConsumptionPoint
import fr.codetics.evapp.models.PortalEvseResponse
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingScreensTests : ScreenTest() {
    private val evse = UUID.fromString("ac5107d1-f3ad-4644-ac98-d57810f8034f")
    private val order = UUID.fromString("7084987b-6653-4d4a-99b7-c1e925397ec2")
    private val portal: PortalEvseResponse
        get() = JsonCoding.decode(PortalEvseResponse.serializer(), FixtureServer.fixture("portal"))

    private fun stopped(reason: String) =
        FixtureServer.fixture("session").replace("\"stopped_at\":null", "\"stopped_at\":\"2026-11-04T10:17:07Z\"").replace("\"stop_reason\":null", "\"stop_reason\":\"$reason\"")

    @Test
    fun theChargeTabListsActiveSessionsAndWaysToStart() {
        connect()
        await { model.charging.active.isNotEmpty() }
        model.guestOrders.add(GuestOrder(order, "secret", "Point QR", Instant.parse("2026-11-04T09:00:00Z")))
        show { ChargeScreen() }
        awaitText(string(R.string.in_progress))
        assertTrue(hasText("Borne Contrat"))
        assertTrue(hasText("11 kW · 3.5 kWh"))
        assertTrue(hasText("Point QR"))
        assertTrue(hasText(string(R.string.point_the_camera_at_the_code_on_the_station)))
        click("Borne Contrat")
        assertEquals(Route.LiveSession(1), top())
        click("Point QR")
        assertTrue(top() is Route.GuestSession)
        clickTag("charge.scan")
        assertEquals(Route.Scanner, top())
        clickTag("charge.enterId")
        assertEquals(Route.ChargingPointLookup(), top())
        click(string(R.string.find_a_station))
        assertEquals(AppTab.Map, model.selectedTab)
    }

    @Test
    fun guestsAreToldNoAccountIsNeeded() {
        connect(signedIn = false)
        show { ChargeScreen() }
        awaitText(string(R.string.no_account_needed_pay_by_card_and_start_charging))
        assertFalse(hasText(string(R.string.in_progress)))
    }

    @Test
    fun theScannerAcceptsPastedLinksAndIds() {
        connect()
        model.selectedTab = AppTab.Charge
        model.open(Route.Scanner, AppTab.Charge)
        show { QrScannerScreen() }
        awaitText(string(R.string.the_camera_is_not_available_on_this_device_paste))
        inputTag("scan.manual", "hello world")
        click(string(R.string.open))
        awaitText(string(R.string.this_qr_code_is_not_a_charging_point_link))
        inputTag("scan.manual", "https://portal.codetics.test/stations/42")
        click(string(R.string.open))
        awaitText(string(R.string.this_link_does_not_point_to_a_charging_point))
        inputTag("scan.manual", "https://portal.codetics.test/charge/${FixtureServer.WORKSPACE}/$evse")
        click(string(R.string.open))
        assertEquals(listOf(Route.Root(AppTab.Charge), Route.Portal(PortalLink(FixtureServer.WORKSPACE, evse))), model.stacks.getValue(AppTab.Charge).toList())
    }

    @Test
    fun scanningAChargingPointIdOpensTheLookup() {
        connect()
        model.open(Route.Scanner, AppTab.Charge)
        show { QrScannerScreen() }
        inputTag("scan.manual", "FR*COD*ECONTRAT11")
        click(string(R.string.open))
        assertEquals(Route.ChargingPointLookup("FR*COD*ECONTRAT11"), top(AppTab.Charge))
    }

    @Test
    fun anotherOperatorsCodeCanBeChargedHereOrOnTheWeb() {
        connect()
        model.open(Route.Scanner, AppTab.Charge)
        show { QrScannerScreen() }
        inputTag("scan.manual", "https://operator.test/start?evse=FR*ABC*E123*1")
        click(string(R.string.open))
        awaitText(string(R.string.charging_point_of_another_operator))
        assertTrue(hasText(string(R.string.this_qr_code_opens_x, "operator.test")))
        clickTag("scan.foreign.app")
        assertEquals(Route.ChargingPointLookup("FR*ABC*E123*1", "https://operator.test/start?evse=FR*ABC*E123*1"), top(AppTab.Charge))
    }

    @Test
    fun theOperatorsWebsiteCanBeOpenedInstead() {
        connect()
        model.open(Route.Scanner, AppTab.Charge)
        show { QrScannerScreen() }
        inputTag("scan.manual", "https://operator.test/start")
        click(string(R.string.open))
        startedIntents()
        clickTag("scan.foreign.web")
        assertTrue(startedIntents().any { it.data.toString() == "https://operator.test/start" })
        assertEquals(Route.Scanner, top(AppTab.Charge))
        clickTag("scan.enterId")
        assertEquals(Route.ChargingPointLookup(), top(AppTab.Charge))
    }

    @Test
    fun aChargingPointShowsItsPriceAndBothWaysToPay() {
        connect()
        show { ChargingPointScreen(PortalLink(FixtureServer.WORKSPACE, evse)) }
        awaitText("Point QR")
        assertTrue(hasText("Réseau Contrat"))
        assertTrue(hasText(string(R.string.available)))
        assertTrue(hasText("€0.39 / kWh"))
        assertTrue(hasText("€1.20 / h"))
        assertTrue(hasText("€0.50"))
        clickTag("portal.badge")
        assertEquals(Route.StartCharge(StartRequest("CONTRAT-1", "Point QR", 1)), top())
        clickTag("portal.guest")
        assertTrue(top() is Route.Checkout)
    }

    @Test
    fun aChargingPointOfAnotherWorkspaceOnlyOffersCardPayment() {
        connect()
        fixtures.respond("GET", "public/evses/other/$evse", FixtureServer.fixture("portal").replace("\"available\":true", "\"available\":false"))
        show { ChargingPointScreen(PortalLink("other", evse)) }
        awaitText("Point QR")
        assertFalse(hasTag("portal.badge"))
        assertTrue(hasText(string(R.string.card_payment_is_not_available_on_this_charging_p)))
    }

    @Test
    fun anUnknownChargingPointShowsTheError() {
        connect(signedIn = false)
        fixtures.fail("GET", "public/evses/${FixtureServer.WORKSPACE}/$evse", 404, "Point de charge introuvable.")
        show { ChargingPointScreen(PortalLink(FixtureServer.WORKSPACE, evse)) }
        awaitText("Point de charge introuvable.")
    }

    @Test
    fun startingWithABadgeOpensTheLiveSession() {
        connect()
        model.open(Route.StartCharge(StartRequest("CONTRAT-1", "Borne Contrat", 1)), AppTab.Charge)
        show { StartChargeScreen(StartRequest("CONTRAT-1", "Borne Contrat", 1)) }
        awaitText("CARTE-1")
        click("CARTE-2")
        clickTag("start.confirm")
        awaitWithLooper { top(AppTab.Charge) is Route.LiveSession }
        assertEquals(Route.LiveSession(1), top(AppTab.Charge))
        assertTrue(fixtures.calls("POST", "transactions/start").single().body.contains("\"tokenUid\":\"04AABBCCDDEE80\""))
    }

    @Test
    fun aRefusedStartIsExplained() {
        connect()
        fixtures.fail("POST", "transactions/start", 409, "Le connecteur est occupé.")
        show { StartChargeScreen(StartRequest("CONTRAT-1", "Borne Contrat", 1)) }
        awaitText("CARTE-1")
        clickTag("start.confirm")
        awaitText("Le connecteur est occupé.")
    }

    @Test
    fun driversWithoutBadgeCannotStart() {
        fixtures.respond("GET", "account/tokens", "[]")
        connect()
        show { StartChargeScreen(StartRequest("CONTRAT-1", "Borne Contrat", 1)) }
        awaitText(string(R.string.you_have_no_active_badge_ask_your_charging_opera))
        compose.onNodeWithTag("start.confirm").assertIsNotEnabled()
    }

    @Test
    fun guestCheckoutCreatesAnOrderAndOpensStripe() {
        connect(signedIn = false)
        show { GuestCheckoutScreen(portal) }
        awaitText(string(R.string.pay_x, "€20.00"))
        assertTrue(hasText("€20.00"))
        startedIntents()
        clickTag("checkout.pay")
        await { model.guestOrders.orders.isNotEmpty() }
        assertEquals("Point QR", model.guestOrders.orders.single().evseName)
        val body = fixtures.calls("POST", "public/evses/${FixtureServer.WORKSPACE}/$evse/checkout").single().body
        assertTrue(body.contains("\"creditMinor\":2000"))
        assertTrue(startedIntents().any { it.data.toString() == "https://checkout.stripe.com/c/pay/guest" })
    }

    @Test
    fun anInsecureCheckoutAddressIsRefused() {
        connect(signedIn = false)
        fixtures.respond("POST", "public/evses/${FixtureServer.WORKSPACE}/$evse/checkout", """{"orderId":"$order","checkoutUrl":"http://checkout.test"}""")
        show { GuestCheckoutScreen(portal) }
        clickTag("checkout.pay")
        awaitText(string(R.string.the_server_returned_an_unexpected_response))
    }

    @Test
    fun aPendingGuestPaymentExplainsWhatHappens() {
        connect(signedIn = false)
        show { GuestSessionScreen(GuestOrder(order, "secret", "Point QR", Instant.now())) }
        awaitText(string(R.string.waiting_for_payment))
        assertTrue(hasText(string(R.string.if_you_closed_the_payment_page_this_request_expi)))
        assertTrue(hasText(string(R.string.reference_x, order.toString())))
        assertTrue(hasText("€20.00"))
        assertFalse(hasTag("guest.stop"))
    }

    @Test
    fun aChargingGuestCanStopTheSession() {
        connect(signedIn = false)
        fixtures.respond("GET", "public/sessions/$order", FixtureServer.fixture("guest").replace("PENDING_PAYMENT", "CHARGING").replace("\"message\":null", "\"message\":\"Borne en maintenance demain\""))
        fixtures.respond("POST", "public/sessions/$order/stop", FixtureServer.fixture("guest").replace("PENDING_PAYMENT", "STOP_REQUESTED"))
        show { GuestSessionScreen(GuestOrder(order, "secret", "Point QR", Instant.now())) }
        awaitText(string(R.string.charging))
        assertTrue(hasText("Borne en maintenance demain"))
        clickTag("guest.stop")
        clickTag("dialog.confirm")
        awaitText(string(R.string.stopping))
        assertEquals("secret", fixtures.calls("POST", "public/sessions/$order/stop").single().headers["X-Charge-Token"])
    }

    @Test
    fun aFinishedGuestSessionCanBeRemoved() {
        connect(signedIn = false)
        val guest = GuestOrder(order, "secret", "Point QR", Instant.now())
        model.guestOrders.add(guest)
        model.open(Route.GuestSession(guest), AppTab.Charge)
        fixtures.respond("GET", "public/sessions/$order", FixtureServer.fixture("guest").replace("PENDING_PAYMENT", "COMPLETED").replace("\"billedMinor\":null", "\"billedMinor\":1200").replace("\"refundedMinor\":0", "\"refundedMinor\":800"))
        show { GuestSessionScreen(guest) }
        awaitText(string(R.string.finished))
        assertTrue(hasText("€12.00"))
        assertTrue(hasText("€8.00"))
        assertTrue(hasText(string(R.string.the_refund_has_been_sent_to_stripe_how_quickly_i)))
        click(string(R.string.remove_from_this_device))
        assertTrue(model.guestOrders.orders.isEmpty())
        assertEquals(Route.Root(AppTab.Charge), top(AppTab.Charge))
    }

    @Test
    fun guestSessionErrorsAreShown() {
        connect(signedIn = false)
        fixtures.fail("GET", "public/sessions/$order", 500, "Paiement introuvable")
        show { GuestSessionScreen(GuestOrder(order, "secret", "Point QR", Instant.now())) }
        awaitText("Paiement introuvable")
    }

    @Test
    fun theLiveSessionShowsMetricsAndCanBeStopped() {
        connect()
        show { LiveSessionScreen(1) }
        awaitText(string(R.string.battery_x, "54"))
        assertTrue(hasText("Borne Contrat"))
        assertTrue(hasText("3.5 kWh"))
        assertTrue(hasText("€1.87"))
        assertTrue(hasText(string(R.string.estimated_price)))
        clickTag("session.stop")
        awaitText(string(R.string.stop_this_charging_session))
        clickTag("dialog.confirm")
        awaitText(string(R.string.the_stop_request_has_been_sent_if_the_station_do))
        assertEquals(1, fixtures.calls("POST", "transactions/1/stop").size)
    }

    @Test
    fun aFinishedSessionShowsWhyItStopped() {
        connect()
        fixtures.respond("GET", "transactions/1", stopped("EVDisconnected"))
        fixtures.respond("GET", "transactions/1/consumptions", """[{"at":"2026-11-04T09:17:07Z","power_kw":7.0},{"at":"2026-11-04T09:27:07Z","power_kw":11.0}]""")
        show { LiveSessionScreen(1) }
        awaitText(string(R.string.session_finished))
        assertTrue(hasText(string(R.string.stop_reason_vehicle_unplugged)))
        assertTrue(hasText(string(R.string.price)))
        assertFalse(hasTag("session.stop"))
        awaitTag("session.chart")
    }

    @Test
    fun liveSessionErrorsAreShown() {
        connect()
        fixtures.fail("GET", "transactions/7", 404, "Session introuvable")
        show { LiveSessionScreen(7) }
        awaitText("Session introuvable")
    }

    @Test
    fun aChargingPointIdLeadsToItsStation() {
        connect()
        show { ChargingPointIdScreen("FR*COD*ECONTRAT11", null) }
        clickTag("chargingPoint.find")
        await { top(AppTab.Charge) == Route.StationDetail("CONTRAT-1") }
        assertEquals(AppTab.Charge, model.selectedTab)
    }

    @Test
    fun guestsAreSentToTheCardPaymentOfTheChargingPoint() {
        connect(signedIn = false)
        show { ChargingPointIdScreen(null, null) }
        inputTag("chargingPoint.code", "fr cod econtrat11")
        clickTag("chargingPoint.find")
        await { top(AppTab.Charge) is Route.Portal }
        val link = (top(AppTab.Charge) as Route.Portal).link
        assertEquals(UUID.fromString("ddbd6eb3-395b-4a37-b5fb-21480cc40d99"), link.evseId)
    }

    @Test
    fun aFreeConnectorGoesStraightToTheStart() {
        fixtures.respond("GET", "account/stations", FixtureServer.fixture("stations").replace("\"current_transaction_id\":5", "\"current_transaction_id\":null"))
        connect()
        show { ChargingPointIdScreen("FR*COD*ECONTRAT11", null) }
        clickTag("chargingPoint.find")
        await { top(AppTab.Charge) is Route.StartCharge }
        assertEquals(Route.StartCharge(StartRequest("CONTRAT-1", "Borne Contrat", 1)), top(AppTab.Charge))
    }

    @Test
    fun anUnknownIdOffersTheOperatorWebsite() {
        connect()
        show { ChargingPointIdScreen("FR*ABC*E123*1", "https://operator.test/start") }
        clickTag("chargingPoint.find")
        awaitText(string(R.string.this_charging_point_is_not_on_x, "Réseau Test"))
        startedIntents()
        click(string(R.string.continue_on_x, "operator.test"))
        assertTrue(startedIntents().any { it.data.toString() == "https://operator.test/start" })
    }

    @Test
    fun stopReasonsAreTranslated() {
        val reasons =
            listOf(
                "Local", "Remote", "EVDisconnected", "EmergencyStop", "PowerLoss", "HardReset", "UnlockCommand", "DeAuthorized",
                "StoppedByEV", "SOCLimitReached", "TimeLimitReached", "EnergyLimitReached", "Timeout", "MasterPass", "LocalOutOfCredit",
                "GroundFault", "OvercurrentFault", "PowerQuality", "StaleSession", "SoftStop", "Other", "Mystery",
            )
        show { Column { reasons.forEach { Text("$it=${stopReasonLabel(it)}") } } }
        assertTrue(hasText("Local=${string(R.string.stop_reason_stopped_at_the_station)}"))
        assertTrue(hasText("HardReset=${string(R.string.stop_reason_station_restarted)}"))
        assertTrue(hasText("LocalOutOfCredit=${string(R.string.stop_reason_credit_exhausted)}"))
        assertTrue(hasText("Mystery=Mystery"))
        assertEquals(reasons.size, reasons.count { hasText("$it=", substring = true) })
    }

    @Test
    fun theChartNeedsAtLeastTwoPowerReadings() {
        val one = listOf(ConsumptionPoint(Instant.now(), powerKw = 7.0))
        val two = one + ConsumptionPoint(Instant.now(), powerKw = 22.0)
        show { Column { ConsumptionChart(one); Text("separator"); ConsumptionChart(two) } }
        assertEquals(1, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("session.chart")).fetchSemanticsNodes().size)
        assertTrue(hasText("22 kW"))
    }
}
