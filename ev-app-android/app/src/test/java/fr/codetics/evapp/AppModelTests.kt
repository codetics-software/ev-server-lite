// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import android.content.Intent
import android.net.Uri
import fr.codetics.evapp.app.AppModel
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.ChargeStartException
import fr.codetics.evapp.app.GuestOrder
import fr.codetics.evapp.app.GuestOrderStore
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.PortalLink
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.core.ConfigurationIssue
import fr.codetics.evapp.core.SecureStore
import fr.codetics.evapp.models.ChargingSessionResponse
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import fr.codetics.evapp.support.configuration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelTests : ScreenTest() {
    private val evse = "ddbd6eb3-395b-4a37-b5fb-21480cc40d99"
    private val order = UUID.fromString("7084987b-6653-4d4a-99b7-c1e925397ec2")

    @Test
    fun theShippedTemplateIsReportedAsMisconfigured() {
        model.bootstrap()
        val state = model.launch as Launch.Misconfigured
        assertTrue(ConfigurationIssue.Placeholder("API_URL") in state.issues)
        assertTrue(ConfigurationIssue.Placeholder("FIREBASE_PROJECT_ID") in state.issues)
        assertNull(model.api)
    }

    @Test
    fun aForeignServerIsIncompatible() {
        fixtures.respond("GET", "public/mobile/configuration", FixtureServer.fixture("configuration").replace("\"api_version\":1", "\"api_version\":2"))
        model.bootstrap(overrides = configuration(fixtures))
        await { model.launch != Launch.Checking }
        assertEquals(Launch.Incompatible, model.launch)
        assertNull(model.server)
    }

    @Test
    fun anUnavailableServerCanBeRetried() {
        fixtures.fail("GET", "public/mobile/configuration", 503, "Maintenance en cours")
        model.bootstrap(overrides = configuration(fixtures))
        await { model.launch is Launch.Unreachable }
        assertEquals("Maintenance en cours", (model.launch as Launch.Unreachable).message)
        assertEquals("Réseau Test", model.brandName)
        model.retryConnection()
        await { model.launch !is Launch.Unreachable }
        assertTrue(model.launch is Launch.Misconfigured)
    }

    @Test
    fun guestsSeeThePublicStationsOfTheWorkspace() {
        connect(signedIn = false)
        assertFalse(model.isSignedIn)
        assertEquals("Borne Contrat", model.stations.station("CONTRAT-1")?.name)
        assertNull(model.stations.station("missing"))
        assertTrue(fixtures.calls("GET", "public/workspaces/${FixtureServer.WORKSPACE}/stations").isNotEmpty())
        assertTrue(fixtures.calls("GET", "account/stations").isEmpty())
    }

    @Test
    fun signingInStoresTheSessionAndLoadsTheAccount() {
        connect()
        assertEquals("DRIVER contrat", model.principal?.name)
        assertEquals("Réseau Contrat", model.workspace?.name)
        assertTrue(model.components?.cars == true)
        assertEquals("fixture-token-not-a-real-session-fixture-token", SecureStore(app, "session").read("bearer"))
        await { model.charging.tokens.isNotEmpty() }
        assertEquals(1, model.charging.current?.id)
        assertEquals(listOf("04A1B2C3D4E580", "04AABBCCDDEE80"), model.charging.usableBadges.map { it.uid })
        val request = fixtures.calls("POST", "auth/app/signin").single()
        assertTrue(request.body.contains("\"email\":\"driver@codetics.test\""))
    }

    @Test
    fun aStoredSessionIsRestoredOnTheNextLaunch() {
        SecureStore(app, "session").write("bearer", "stored-token")
        model.bootstrap(overrides = configuration(fixtures))
        await { model.isSignedIn && model.workspace != null }
        assertEquals("Bearer stored-token", fixtures.calls("GET", "auth/me").first().headers["Authorization"])
        awaitCall("GET", "account/stations")
    }

    @Test
    fun aRevokedStoredSessionIsForgotten() {
        SecureStore(app, "session").write("bearer", "revoked-token")
        fixtures.fail("GET", "auth/me", 401, "Session expirée")
        model.bootstrap(overrides = configuration(fixtures))
        await { model.launch == Launch.Ready && fixtures.calls("GET", "auth/me").isNotEmpty() }
        await { SecureStore(app, "session").read("bearer") == null }
        assertFalse(model.isSignedIn)
    }

    @Test
    fun anExpiredSessionSignsTheDriverOut() {
        connect()
        model.open(Route.Vehicles, AppTab.Account)
        fixtures.fail("GET", "account/stations", 401, "expired")
        runOnModel { model.stations.reload() }
        assertFalse(model.isSignedIn)
        assertEquals(string(R.string.your_session_has_expired_sign_in_again), model.alertMessage)
        assertEquals(1, model.stacks.getValue(AppTab.Account).size)
        assertEquals(AppTab.Map, model.selectedTab)
        assertNull(SecureStore(app, "session").read("bearer"))
    }

    @Test
    fun stationErrorsAreKeptForTheMap() {
        connect()
        fixtures.fail("GET", "account/stations", 500, "Base indisponible")
        runOnModel { model.stations.reload() }
        assertEquals("Base indisponible", model.stations.errorMessage)
        assertFalse(model.stations.isLoading)
        assertEquals(1, model.stations.stations.size)
    }

    @Test
    fun signingOutEverywhereCallsTheServerAndClearsTheState() {
        connect()
        model.open(Route.Profile, AppTab.Account)
        runOnModel { model.signOut(everywhere = true) }
        assertEquals(1, fixtures.calls("POST", "auth/signout/all").size)
        assertFalse(model.isSignedIn)
        assertNull(model.workspace)
        assertTrue(model.charging.active.isEmpty())
        assertEquals(listOf<Route>(Route.Root(AppTab.Account)), model.stacks.getValue(AppTab.Account).toList())
    }

    @Test
    fun deletingTheAccountForgetsTheSession() {
        connect()
        runOnModel { model.deleteAccount() }
        assertEquals(1, fixtures.calls("DELETE", "account").size)
        assertFalse(model.isSignedIn)
    }

    @Test
    fun acceptingTheEulaRefreshesThePrincipal() {
        fixtures.respond("POST", "auth/app/signin", FixtureServer.signIn(eulaAccepted = false))
        connect()
        assertEquals(false, model.principal?.eulaAccepted)
        runOnModel { model.acceptEula() }
        assertEquals(1, fixtures.calls("POST", "auth/eula/accept").size)
        assertEquals(true, model.principal?.eulaAccepted)
    }

    @Test
    fun aChargingLinkOpenedBeforeLaunchIsShownOnceReady() {
        model.handle(Uri.parse("https://portal.codetics.test/charge/${FixtureServer.WORKSPACE}/$evse"))
        model.bootstrap(overrides = configuration(fixtures))
        await { model.launch == Launch.Ready && model.stacks.getValue(AppTab.Charge).size == 2 }
        assertEquals(AppTab.Charge, model.selectedTab)
        assertEquals(Route.Portal(PortalLink(FixtureServer.WORKSPACE, UUID.fromString(evse))), top(AppTab.Charge))
    }

    @Test
    fun linksOfOtherNetworksAreIgnored() {
        connect(signedIn = false)
        model.handle(Uri.parse("https://elsewhere.test/charge/${FixtureServer.WORKSPACE}/$evse"))
        model.handle(Intent())
        model.handle(null as Intent?)
        assertEquals(1, model.stacks.getValue(AppTab.Charge).size)
        model.handle(Intent(Intent.ACTION_VIEW, Uri.parse("https://portal.codetics.test/charge/${FixtureServer.WORKSPACE}/$evse")))
        assertTrue(top(AppTab.Charge) is Route.Portal)
    }

    @Test
    fun notificationsOpenTheLiveSessionOnlyWhenSignedIn() {
        connect(signedIn = false)
        model.handle(Intent().putExtra("transaction_id", "42"))
        assertEquals(AppTab.Map, model.selectedTab)
        model.scope.launch { model.signIn(FixtureServer.WORKSPACE, "driver@codetics.test", "a-long-enough-password") }
        await { model.isSignedIn }
        model.open(Route.Vehicles, AppTab.Charge)
        model.handle(Intent().putExtra("transaction_id", "42"))
        assertEquals(AppTab.Charge, model.selectedTab)
        assertEquals(listOf(Route.Root(AppTab.Charge), Route.LiveSession(42)), model.stacks.getValue(AppTab.Charge).toList())
        model.openNotification(null)
        assertEquals(2, model.stacks.getValue(AppTab.Charge).size)
    }

    @Test
    fun navigationStacksAreKeptPerTab() {
        model.open(Route.About, AppTab.Account)
        model.open(Route.Profile)
        assertEquals(3, model.stacks.getValue(AppTab.Account).size)
        assertTrue(model.back())
        assertTrue(model.back())
        assertFalse(model.back())
        model.open(Route.Scanner, AppTab.Charge)
        model.resetStacks()
        assertTrue(model.stacks.values.all { it.size == 1 })
    }

    @Test
    fun errorsAreExplainedToTheDriver() {
        assertEquals(string(R.string.your_session_has_expired_sign_in_again), model.message(ApiError.Unauthorized))
        assertEquals("Borne introuvable", model.message(ApiError.Server(404, "Borne introuvable")))
        assertEquals(string(R.string.the_server_refused_this_request), model.message(ApiError.Server(500, null)))
        assertEquals(string(R.string.the_server_cannot_be_reached_check_your_connecti), model.message(ApiError.Offline))
        assertEquals(string(R.string.the_server_returned_an_unexpected_response), model.message(ApiError.InvalidResponse))
        assertEquals(string(R.string.the_station_has_not_confirmed_the_start_check_th), model.message(ChargeStartException()))
        assertEquals("boom", model.message(IllegalStateException("boom")))
        assertEquals(string(R.string.the_server_returned_an_unexpected_response), model.message(IllegalStateException()))
        assertEquals("EV App", model.brandName)
    }

    @Test
    fun startingAChargeWaitsForTheStationToConfirm() {
        connect()
        var started: ChargingSessionResponse? = null
        val job = model.scope.launch { started = model.charging.start("CONTRAT-1", 1, "04A1B2C3D4E580") }
        awaitWithLooper { job.isCompleted }
        assertEquals(1, started?.id)
        val start = fixtures.calls("POST", "transactions/start").single()
        assertTrue(start.body.contains("\"tokenUid\":\"04A1B2C3D4E580\""))
        assertTrue(fixtures.calls("GET", "transactions").any { it.query?.contains("stationId=CONTRAT-1") == true })
    }

    @Test
    fun aStartThatIsNeverConfirmedTimesOut() {
        connect()
        fixtures.respond("GET", "transactions", """{"items":[],"total":0}""")
        val result = model.scope.async { runCatching { model.charging.start("CONTRAT-1", 1, "04A1B2C3D4E580") } }
        awaitWithLooper(timeoutMillis = 60_000, step = java.time.Duration.ofSeconds(10)) { result.isCompleted }
        assertTrue(result.let { kotlinx.coroutines.runBlocking { it.await() } }.exceptionOrNull() is ChargeStartException)
    }

    @Test
    fun guestOrdersArePersistedAndPrunedOnRefresh() {
        connect(signedIn = false)
        val store = model.guestOrders
        val recent = GuestOrder(order, "secret", "Point QR", Instant.now())
        val gone = GuestOrder(UUID.randomUUID(), "other", "Ancien point", Instant.now())
        store.add(recent)
        store.add(gone)
        store.add(recent.copy(evseName = "Point QR renommé"))
        assertEquals(listOf("Point QR renommé", "Ancien point"), store.orders.map { it.evseName })
        assertEquals(store.orders, GuestOrderStore(model, app).orders)
        fixtures.fail("GET", "public/sessions/${gone.id}", 404, "Session introuvable.")
        runOnModel { store.refresh() }
        assertEquals(listOf(order), store.orders.map { it.id })
        store.remove(order)
        assertTrue(GuestOrderStore(model, app).orders.isEmpty())
    }

    @Test
    fun finishedGuestOrdersExpireAfterADay() {
        connect(signedIn = false)
        fixtures.respond("GET", "public/sessions/$order", FixtureServer.fixture("guest").replace("PENDING_PAYMENT", "COMPLETED"))
        model.guestOrders.add(GuestOrder(order, "secret", "Point QR", Instant.now().minusSeconds(200_000)))
        runOnModel { model.guestOrders.refresh() }
        assertTrue(model.guestOrders.orders.isEmpty())
        assertEquals("secret", fixtures.calls("GET", "public/sessions/$order").last().headers["X-Charge-Token"])
    }

    @Test
    fun pushTokensAreRegisteredForSignedInDrivers() {
        connect()
        runOnModel { model.push.deviceRegistered("fcm-token") }
        assertEquals("fcm-token", model.push.deviceToken)
        assertNull(model.push.lastError)
        val body = fixtures.calls("POST", "account/devices").single().body
        assertTrue(body.contains("\"platform\":\"ANDROID\""))
        assertTrue(body.contains("\"environment\":\"sandbox\""))
        fixtures.fail("POST", "account/devices", 500, "Push indisponible")
        runOnModel { model.push.deviceRegistered("fcm-token-2") }
        assertEquals("Push indisponible", model.push.lastError)
        runOnModel { model.signOut() }
        assertEquals(1, fixtures.calls("DELETE", "account/devices/fcm-token-2").size)
    }

    @Test
    fun pushTokensWaitForASignIn() {
        connect(signedIn = false)
        runOnModel { model.push.deviceRegistered("fcm-token") }
        assertEquals("fcm-token", model.push.deviceToken)
        assertTrue(fixtures.calls("POST", "account/devices").isEmpty())
        assertFalse(model.push.permissionRequest)
    }

    @Test
    fun secureStoreEncryptsAndForgetsValues() {
        val store = SecureStore(app, "tests")
        assertNull(store.read("missing"))
        store.write("secret", "valeur confidentielle")
        val raw = app.getSharedPreferences("secure_tests", 0).getString("secret", null)
        assertNotNull(raw)
        assertFalse(raw!!.contains("confidentielle"))
        assertEquals("valeur confidentielle", store.read("secret"))
        store.delete("secret")
        assertNull(store.read("secret"))
        app.getSharedPreferences("secure_tests", 0).edit().putString("broken", "bm90IGEgdmFsaWQgYmxvYg==").commit()
        assertNull(store.read("broken"))
        assertFalse(app.getSharedPreferences("secure_tests", 0).contains("broken"))
    }

    @Test
    fun aModelWithoutApiDoesNothing() {
        val fresh = AppModel(app)
        runOnModel {
            fresh.stations.reload()
            fresh.charging.refresh()
            fresh.guestOrders.refresh()
            fresh.refreshPrincipal()
        }
        assertTrue(fresh.stations.stations.isEmpty())
        assertNull(fresh.userLocation)
        assertFalse(fresh.location.hasPermission)
        val failure = model.scope.async { runCatching { fresh.signIn("w", "e", "p") } }
        await { failure.isCompleted }
        assertEquals(ApiError.InvalidResponse, failure.let { kotlinx.coroutines.runBlocking { it.await() } }.exceptionOrNull())
    }
}
