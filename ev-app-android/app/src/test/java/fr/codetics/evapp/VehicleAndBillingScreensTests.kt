// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.features.billing.InvoicesScreen
import fr.codetics.evapp.features.billing.PaymentMethodsScreen
import fr.codetics.evapp.features.notifications.NotificationPreferencesScreen
import fr.codetics.evapp.features.notifications.NotificationsScreen
import fr.codetics.evapp.features.vehicles.VehicleDetailScreen
import fr.codetics.evapp.features.vehicles.VehicleEditorScreen
import fr.codetics.evapp.features.vehicles.VehiclesScreen
import fr.codetics.evapp.models.CarData
import fr.codetics.evapp.models.RecordResponse
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleAndBillingScreensTests : ScreenTest() {
    private val carId = "17e97d10-b40a-4f65-b976-840049b5fc56"
    private val car: RecordResponse<CarData>
        get() = JsonCoding.decode(ListSerializer(RecordResponse.serializer(CarData.serializer())), FixtureServer.fixture("cars")).single()

    private val invoice =
        """{"id":"5b0e9a52-65b3-4143-bf1a-d957fe1e3750","number":"F-2026-001","user_id":"0720d0ed-c8e8-419c-8e01-0c1d0ea9dc6d","status":"OPEN","amount_minor":1250,"currency":"EUR","created_at":"2026-11-04T10:00:00Z","session_ids":[1],"hosted_url":"https://invoice.stripe.com/i/open"}"""

    @Test
    fun vehiclesAreListedAndCanBeDeleted() {
        fixtures.respond("GET", "account", FixtureServer.fixture("account").replace("\"default_car_id\":null", "\"default_car_id\":\"$carId\""))
        connect()
        show { VehiclesScreen() }
        awaitText("Zoé")
        assertTrue(hasText("AA-123-BB · 52 kWh · ${string(R.string.default_label)}"))
        click("Zoé")
        assertEquals("Zoé", (top() as Route.VehicleDetail).car.name)
        clickTag("vehicles.add")
        assertEquals(Route.VehicleEditor(null), top())
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(string(R.string.delete)))[0].performClick()
        awaitText(string(R.string.delete_this_vehicle))
        clickTag("dialog.confirm")
        awaitCall("DELETE", "account/cars/$carId")
    }

    @Test
    fun driversWithoutVehiclesCanAddOne() {
        fixtures.respond("GET", "account/cars", "[]")
        connect()
        show { VehiclesScreen() }
        awaitText(string(R.string.no_vehicle))
        compose.onAllNodesWithText(string(R.string.add_a_vehicle)).onLast().performClick()
        assertEquals(Route.VehicleEditor(null), top())
    }

    @Test
    fun vehicleErrorsAreShown() {
        fixtures.fail("GET", "account/cars", 500, "Véhicules indisponibles")
        connect()
        show { VehiclesScreen() }
        awaitText("Véhicules indisponibles")
    }

    @Test
    fun vehicleDetailsListSpecsAndAutoCharge() {
        fixtures.respond("GET", "account/cars/$carId/autocharge", """[{"uid":"VID:0011223344","vehicle_id":"$carId","enabled":true}]""")
        fixtures.respond("GET", "account/autocharge/candidates", """[{"transaction_id":9,"station_id":"CONTRAT-1","connector_id":1,"started_at":"2026-11-04T09:00:00Z","vid":"VID:99"}]""")
        connect()
        model.open(Route.VehicleDetail(car), AppTab.Account)
        show { VehicleDetailScreen(car.copy(data = car.data.copy(vin = "VF1AG000000000001", maxAcPowerKw = 22.0, maxDcPowerKw = 50.0))) }
        awaitText("VID:0011223344")
        assertTrue(hasText("Renault"))
        assertTrue(hasText("VF1AG000000000001"))
        assertTrue(hasText("22 kW"))
        assertTrue(hasText("50 kW"))
        click(string(R.string.enable_with_the_session_on_x, "CONTRAT-1"))
        assertTrue(awaitCall("POST", "account/cars/$carId/autocharge").body.contains("\"transactionId\":9"))
        click(string(R.string.remove))
        awaitCall("DELETE", "account/cars/$carId/autocharge/VID:0011223344")
        click(string(R.string.edit))
        assertEquals(Route.VehicleEditor(car.copy(data = car.data.copy(vin = "VF1AG000000000001", maxAcPowerKw = 22.0, maxDcPowerKw = 50.0))), top(AppTab.Account))
    }

    @Test
    fun aVehicleWithoutAutoChargeExplainsHowToEnableIt() {
        connect()
        show { VehicleDetailScreen(car) }
        awaitText(string(R.string.plug_this_vehicle_into_a_compatible_station_and))
    }

    @Test
    fun aNewVehicleIsCreatedFromTheCatalog() {
        connect()
        model.open(Route.VehicleEditor(null), AppTab.Account)
        show { VehicleEditorScreen(null) }
        awaitText(string(R.string.new_vehicle))
        clickTag("vehicle.catalog")
        inputTag("catalog.search", "zoe")
        compose.mainClock.advanceTimeBy(400)
        awaitTag("catalog.row")
        clickTag("catalog.row")
        input(string(R.string.licence_plate), "ab-123-cd")
        clickToggle()
        clickTag("vehicle.save")
        val body = awaitCall("POST", "account/cars").body
        assertTrue(body, body.contains("\"name\":\"Renault Zoé R135\""))
        assertTrue(body, body.contains("\"carCatalogId\":\"1f089676-9dea-41fb-a811-15be404ae008\""))
        assertTrue(body, body.contains("\"licensePlate\":\"AB-123-CD\""))
        assertTrue(body, body.contains("\"maxDcPowerKw\":50.0"))
        assertTrue(body, body.contains("\"default\":true"))
        await { top(AppTab.Account) == Route.Root(AppTab.Account) }
    }

    @Test
    fun anExistingVehicleIsUpdatedWithItsRevision() {
        connect()
        show { VehicleEditorScreen(car) }
        awaitText(string(R.string.edit_vehicle))
        input(string(R.string.battery_kwh), "54,5")
        clickTag("vehicle.save")
        val body = awaitCall("PUT", "account/cars/$carId").body
        assertTrue(body, body.contains("\"batteryKwh\":54.5"))
        assertTrue(body, body.contains("\"revision\":0"))
        assertTrue(body, body.contains("\"name\":\"Zoé\""))
    }

    @Test
    fun savingFailuresAreShown() {
        connect()
        fixtures.fail("POST", "account/cars", 400, "Plaque invalide")
        show { VehicleEditorScreen(null) }
        clickTag("vehicle.save")
        awaitText("Plaque invalide")
        assertTrue(fixtures.calls("POST", "account/cars").single().body.contains("\"name\":\"${string(R.string.vehicle)}\""))
    }

    @Test
    fun paymentMethodsCanBeAddedMadeDefaultAndDeleted() {
        connect()
        show { PaymentMethodsScreen() }
        awaitText("Visa •••• 4242")
        assertTrue(hasText("Mastercard •••• 4444"))
        assertTrue(hasText(string(R.string.expires_x_x, "8", "2029")))
        click(string(R.string.set_as_default))
        awaitCall("POST", "account/payment-methods/pm_2/default")
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(string(R.string.delete))).onLast().performClick()
        awaitText(string(R.string.delete_this_payment_method))
        clickTag("dialog.confirm")
        awaitCall("DELETE", "account/payment-methods/pm_2")
        startedIntents()
        clickTag("payments.add")
        awaitCall("POST", "account/payment-methods/checkout")
        await { startedIntents().any { it.data.toString() == "https://checkout.stripe.com/c/pay/test" } }
    }

    @Test
    fun paymentMethodErrorsAreShown() {
        fixtures.fail("GET", "account/payment-methods", 503, "Stripe indisponible")
        connect()
        show { PaymentMethodsScreen() }
        awaitText("Stripe indisponible")
    }

    @Test
    fun anEmptyInvoiceListSaysSo() {
        connect()
        show { InvoicesScreen() }
        awaitText(string(R.string.no_invoices))
    }

    @Test
    fun openInvoicesArePaidOnStripeAndOthersDownloaded() {
        val paid = invoice.replace("5b0e9a52", "6b0e9a52").replace("\"OPEN\"", "\"PAID\"").replace("F-2026-001", "F-2026-002")
        fixtures.respond("GET", "invoices", """{"items":[$invoice,$paid],"total":2}""")
        connect()
        show { InvoicesScreen() }
        awaitText("F-2026-001")
        assertTrue(hasText(string(R.string.to_pay)))
        assertTrue(hasText("€12.50"))
        startedIntents()
        click("F-2026-001")
        await { startedIntents().any { it.data.toString() == "https://invoice.stripe.com/i/open" } }
        click("F-2026-002")
        awaitCall("GET", "invoices/6b0e9a52-65b3-4143-bf1a-d957fe1e3750/download")
        await { startedIntents().any { it.data.toString() == "https://invoices.stripe.com/i/test" } }
    }

    @Test
    fun invoiceErrorsAreShown() {
        fixtures.fail("GET", "invoices", 500, "Factures indisponibles")
        connect()
        show { InvoicesScreen() }
        awaitText("Factures indisponibles")
    }

    @Test
    fun notificationsListTheHistoryAndOpenPreferences() {
        connect()
        show { NotificationsScreen() }
        awaitText("Votre session de recharge a commencé")
        clickTag("notifications.preferences")
        assertEquals(Route.NotificationPreferences, top())
    }

    @Test
    fun anEmptyNotificationHistorySaysSo() {
        fixtures.respond("GET", "notifications", """{"items":[],"total":0}""")
        connect()
        show { NotificationsScreen() }
        awaitText(string(R.string.no_notifications_yet))
    }

    @Test
    fun preferencesAreSavedOnEachToggle() {
        connect()
        show { NotificationPreferencesScreen() }
        awaitText("Début de session")
        assertTrue(hasText(string(R.string.this_server_does_not_send_push_notifications_yet)))
        assertFalse(hasText("Borne hors ligne"))
        val toggles = compose.onAllNodes(isToggleable())
        toggles[1].performClick()
        assertTrue(awaitCall("PUT", "account/notifications").body.contains("\"email\":false"))
        toggles[2].performClick()
        await { fixtures.calls("PUT", "account/notifications").any { it.body.contains("\"SESSION_STARTED\":false") } }
    }

    @Test
    fun preferenceErrorsAreShown() {
        connect()
        fixtures.fail("GET", "account/notifications", 500, "Préférences indisponibles")
        show { NotificationPreferencesScreen() }
        awaitText("Préférences indisponibles")
    }
}
