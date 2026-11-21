// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.OnClickDelegate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.OnDoneCallback
import androidx.car.app.testing.TestCarContext
import com.google.firebase.messaging.RemoteMessage
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.Coordinate
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.car.SessionCarScreen
import fr.codetics.evapp.car.StationsCarScreen
import fr.codetics.evapp.car.connectorSummary
import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.features.map.framingPoints
import fr.codetics.evapp.features.map.pinImage
import fr.codetics.evapp.features.map.usesGoogleMaps
import fr.codetics.evapp.models.CustomerStationResponse
import fr.codetics.evapp.push.EvMessagingService
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

class AppShellTests : ScreenTest() {
    private val stations: List<CustomerStationResponse>
        get() = JsonCoding.decode(ListSerializer(CustomerStationResponse.serializer()), FixtureServer.fixture("stations"))

    @Test
    fun signedInDriversGetEveryTabAndTheSessionBar() {
        connect()
        await { model.charging.current != null }
        showRoot(AppTab.Charge)
        awaitTag("tab.sessions")
        assertTrue(hasTag("tab.badges"))
        awaitTag("session.accessory")
        assertTrue(hasText("11 kW · 3.5 kWh"))
        clickTag("session.accessory")
        assertEquals(Route.LiveSession(1), top(AppTab.Charge))
        await { !hasTag("session.accessory") }
        clickTag("tab.charge")
        assertEquals(Route.Root(AppTab.Charge), top(AppTab.Charge))
        clickTag("tab.account")
        assertEquals(AppTab.Account, model.selectedTab)
        awaitText("DRIVER contrat")
        clickTag("tab.sessions")
        awaitTag("session.row.1")
        clickTag("tab.badges")
        awaitText("CARTE-1")
    }

    @Test
    fun guestsOnlySeeThePublicTabs() {
        connect(signedIn = false)
        showRoot(AppTab.Charge)
        awaitTag("tab.charge")
        assertFalse(hasTag("tab.sessions"))
        assertFalse(hasTag("session.accessory"))
    }

    @Test
    fun alertsAreShownUntilDismissed() {
        connect(signedIn = false)
        showRoot(AppTab.Account)
        model.alertMessage = "Coupure réseau"
        awaitText(string(R.string.something_went_wrong))
        assertTrue(hasText("Coupure réseau"))
        click(string(R.string.ok))
        assertNull(model.alertMessage)
    }

    @Test
    fun everyRouteHasADestination() {
        connect()
        showRoot(AppTab.Account)
        val car = JsonCoding.decode(ListSerializer(fr.codetics.evapp.models.RecordResponse.serializer(fr.codetics.evapp.models.CarData.serializer())), FixtureServer.fixture("cars")).single()
        val routes =
            listOf(
                Route.About to "EV Supervision Lite — built by Kevin Vavelin, Codetics",
                Route.Sites to "Gare",
                Route.Profile to string(R.string.profile),
                Route.Notifications to "Votre session de recharge a commencé",
                Route.NotificationPreferences to "Début de session",
                Route.Invoices to string(R.string.no_invoices),
                Route.PaymentMethods to "Visa •••• 4242",
                Route.Vehicles to "AA-123-BB · 52 kWh",
                Route.VehicleDetail(car) to "Renault",
                Route.VehicleEditor(car) to string(R.string.edit_vehicle),
                Route.ActivateCard to string(R.string.or_enter_its_number),
                Route.Report("CONTRAT-1") to string(R.string.station_x, "CONTRAT-1"),
                Route.StationDetail("CONTRAT-1") to "1 place de la Gare, 69002 Lyon",
                Route.LiveSession(1) to string(R.string.battery_x, "54"),
                Route.ChargingPointLookup() to string(R.string.find_this_charging_point),
                Route.Scanner to string(R.string.enter_the_charging_point_id),
                Route.SignUp to string(R.string.i_accept_the_terms_of_use_and_the_privacy_policy),
                Route.PasswordReset(FixtureServer.WORKSPACE, "") to string(R.string.forgot_password_98f130),
            )
        routes.forEach { (route, expected) ->
            model.open(route, AppTab.Account)
            awaitText(expected, substring = true)
            model.back(AppTab.Account)
        }
    }

    @Test
    fun theLocationOfTheDriverIsFollowedOncePermitted() {
        val location = model.location
        location.start()
        assertNull(model.userLocation)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(location.hasPermission)
        val manager = app.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        location.start()
        val fix = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 45.76
            longitude = 4.84
            time = System.currentTimeMillis()
        }
        shadowOf(manager).simulateLocation(fix)
        settleLooper()
        assertEquals(Coordinate(45.76, 4.84), model.userLocation)
        location.stop()
        location.stop()
    }

    @Test
    fun mapHelpersFrameTheNearestStationsAndDrawPins() {
        val user = Coordinate(45.0, 4.0)
        val points = framingPoints(stations, user)
        assertEquals(listOf(Coordinate(45.76, 4.83), user), points)
        assertEquals(listOf(Coordinate(45.76, 4.83)), framingPoints(stations, null))
        assertTrue(framingPoints(emptyList(), null).isEmpty())
        val pin = pinImage(app, android.graphics.Color.GREEN)
        val expected = (34 * app.resources.displayMetrics.density).toInt()
        assertEquals(expected, pin.width)
        assertEquals(expected, pin.height)
        assertFalse(usesGoogleMaps(app, null))
        assertFalse(usesGoogleMaps(app, " "))
        assertFalse(usesGoogleMaps(app, "AIza-test"))
    }

    @Test
    fun carRowsSummariseConnectors() {
        val context = TestCarContext.createCarContext(app)
        val station = stations.single()
        assertEquals(listOf("${string(R.string.connector)} · 1/1"), context.connectorSummary(station))
        assertEquals(listOf("${string(R.string.connector)} · 0/1"), context.connectorSummary(station.copy(connected = false)))
        val typed = station.copy(connectors = station.connectors.map { it.copy(type = "T2", maximumPowerKw = 22.0, status = "Charging") })
        assertEquals(listOf("Type 2 22 kW · 0/1"), context.connectorSummary(typed))
    }

    @Test
    fun theCarStationListCanShowOnlyAvailableStations() {
        connect()
        val screen = StationsCarScreen(TestCarContext.createCarContext(app), model)
        val template = screen.onGetTemplate() as PlaceListMapTemplate
        assertEquals("Borne Contrat", (template.itemList!!.items.single() as androidx.car.app.model.Row).title.toString())
        val toggle = template.actionStrip!!.actions.first()
        assertEquals(string(R.string.all), toggle.title.toString())
        click(toggle.onClickDelegate)
        val filtered = screen.onGetTemplate() as PlaceListMapTemplate
        assertEquals(string(R.string.available), filtered.actionStrip!!.actions.first().title.toString())
        assertTrue(filtered.actionStrip!!.actions[1].title.toString().startsWith(string(R.string.charging)))
    }

    @Test
    fun theCarSessionCanBeStoppedAfterConfirmation() {
        connect()
        await { model.charging.current != null }
        val screen = SessionCarScreen(TestCarContext.createCarContext(app), model)
        val pane = (screen.onGetTemplate() as PaneTemplate).pane
        assertEquals(4, pane.rows.size)
        assertEquals("Borne Contrat", pane.rows.first().texts.first().toString())
        click(pane.actions.single().onClickDelegate)
        val confirmation = screen.onGetTemplate() as MessageTemplate
        assertEquals(string(R.string.stop_this_charging_session), confirmation.message.toString())
        click(confirmation.actions[1].onClickDelegate)
        assertTrue(screen.onGetTemplate() is PaneTemplate)
        click((screen.onGetTemplate() as PaneTemplate).pane.actions.single().onClickDelegate)
        click((screen.onGetTemplate() as MessageTemplate).actions[0].onClickDelegate)
        awaitCall("POST", "transactions/1/stop")
    }

    @Test
    fun theCarSaysWhenNothingIsCharging() {
        fixtures.respond("GET", "transactions", """{"items":[],"total":0}""")
        connect()
        val template = SessionCarScreen(TestCarContext.createCarContext(app), model).onGetTemplate() as MessageTemplate
        assertTrue(template.message.toString().startsWith(string(R.string.no_charging_in_progress)))
    }

    @Test
    fun pushMessagesRefreshAndNotifyTheDriver() {
        connect()
        val service = Robolectric.setupService(EvMessagingService::class.java)
        val before = fixtures.calls("GET", "transactions").size
        val bundle = Bundle().apply {
            putString("gcm.n.e", "1")
            putString("gcm.n.title", "Recharge terminée")
            putString("gcm.n.body", "Votre véhicule est chargé.")
            putString("transaction_id", "1")
            putString("type", "END_OF_CHARGE")
        }
        service.onMessageReceived(RemoteMessage(bundle))
        await { fixtures.calls("GET", "transactions").size > before }
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, notifications.size)
        assertEquals("Recharge terminée", shadowOf(notifications.single()).contentTitle.toString())
        service.onMessageReceived(RemoteMessage(Bundle().apply { putString("type", "SILENT") }))
        assertEquals(1, shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.size)
        service.onNewToken("rotated-token")
        awaitCall("POST", "account/devices")
        assertEquals("rotated-token", model.push.deviceToken)
    }

    @Test
    fun theActivityStartsOnTheConfigurationCheck() {
        val intent = Intent(app, MainActivity::class.java).putExtra("transaction_id", "3")
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        assertTrue(model.launch is Launch.Misconfigured)
        controller.newIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://portal.codetics.test/charge/x/y")))
        controller.pause().stop().destroy()
    }

    private fun settleLooper() {
        repeat(5) { shadowOf(android.os.Looper.getMainLooper()).idle() }
    }

    private fun click(delegate: OnClickDelegate?) {
        delegate!!.sendClick(object : OnDoneCallback {})
        await { true }
        settleLooper()
    }
}
