// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import fr.codetics.evapp.core.ApiClient
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.core.EvServerApi
import fr.codetics.evapp.models.*
import fr.codetics.evapp.support.FixtureServer
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerApiTests {
    private val fixtures = FixtureServer()
    private lateinit var api: EvServerApi
    private val carId = UUID.fromString("17e97d10-b40a-4f65-b976-840049b5fc56")
    private val evse = UUID.fromString("ac5107d1-f3ad-4644-ac98-d57810f8034f")
    private val order = UUID.fromString("7084987b-6653-4d4a-99b7-c1e925397ec2")

    @Before
    fun start() {
        fixtures.start()
        api = EvServerApi(ApiClient(fixtures.url, language = { "fr" }))
    }

    @After
    fun stop() {
        fixtures.shutdown()
    }

    @Test
    fun readsTheAccountEndpoints() = runBlocking {
        assertEquals("ev-supervision-lite", api.configuration().server)
        val session = api.signIn(LoginRequest(FixtureServer.WORKSPACE, "a@b.c", "secret"))
        api.client.bearerToken = session.token
        assertEquals("DRIVER contrat", api.me().name)
        assertEquals("1.0", api.eula().version)
        assertEquals(FixtureServer.WORKSPACE, api.workspace().slug)
        assertEquals("fr", api.profile().locale)
        assertEquals("DRIVER contrat", api.updateProfile(ProfileInput("DRIVER contrat")).displayName)
        assertEquals("Gare", api.sites().single().name)
        api.acceptEula()
        api.signUp(SignupRequest("a@b.c", "Ann", "secret"))
        api.requestPasswordReset(ChallengeRequest(FixtureServer.WORKSPACE, "a@b.c"))
        api.resendVerification(ChallengeRequest(FixtureServer.WORKSPACE, "a@b.c"))
        api.signOut()
        api.signOutEverywhere()
        api.deleteAccount()
        val signIn = fixtures.calls("POST", "auth/app/signin").single()
        assertTrue(signIn.body.contains("\"tenant\":\"${FixtureServer.WORKSPACE}\""))
        assertEquals("Bearer fixture-token-not-a-real-session-fixture-token", fixtures.calls("GET", "auth/me").single().headers["Authorization"])
        assertEquals("fr", fixtures.calls("GET", "auth/me").single().headers["Accept-Language"])
        assertEquals(1, fixtures.calls("GET", "auth/csrf").size)
        listOf("auth/eula/accept", "auth/signon", "auth/password/reset/request", "auth/mail/resend", "auth/signout", "auth/signout/all").forEach {
            assertEquals(it, 1, fixtures.calls("POST", it).size)
        }
        assertEquals(1, fixtures.calls("DELETE", "account").size)
    }

    @Test
    fun readsStationsPhotosAndSearches() = runBlocking {
        assertEquals("CONTRAT-1", api.stations().single().id)
        api.stations("Lyon")
        assertEquals("search=Lyon", fixtures.calls("GET", "account/stations").last().query)
        assertNull(fixtures.calls("GET", "account/stations").first().query)
        assertEquals("Borne Contrat", api.station("CONTRAT-1").name)
        assertEquals("Gare", api.publicStations(FixtureServer.WORKSPACE).single().siteName)
        assertEquals("CONTRAT-1", api.publicStation(FixtureServer.WORKSPACE, "CONTRAT-1").id)
        val photo = api.photos("CONTRAT-1").single()
        assertTrue(photo.mine)
        assertEquals(photo.id, api.uploadPhoto("CONTRAT-1", byteArrayOf(1, 2, 3)).id)
        val upload = fixtures.calls("POST", "account/stations/CONTRAT-1/photos").single()
        assertEquals("image/jpeg", upload.headers["Content-Type"])
        fixtures.respond("GET", "account/stations/CONTRAT-1/photos/${photo.id}") {
            mockwebserver3.MockResponse.Builder().code(200).addHeader("Content-Type", "image/jpeg").body(okio.Buffer().write(byteArrayOf(9, 8, 7))).build()
        }
        assertArrayEquals(byteArrayOf(9, 8, 7), api.photoData(photo))
        api.deletePhoto(photo)
        assertEquals(1, fixtures.calls("DELETE", "account/stations/CONTRAT-1/photos/${photo.id}").size)
    }

    @Test
    fun readsSessionsStatisticsAndCommands() = runBlocking {
        val page = api.sessions(status = "active", stationId = "CONTRAT-1", limit = 10)
        assertEquals(1, page.total)
        assertEquals("limit=10&offset=0&stationId=CONTRAT-1&status=active", fixtures.calls("GET", "transactions").single().query)
        assertEquals(54, api.session(1).stateOfCharge)
        assertEquals(11.0, api.consumptions(1).single().powerKw!!, 0.0)
        assertEquals("OCMF", api.signedData(1).format)
        assertEquals("QUEUED", api.startSession(SessionStartInput("CONTRAT-1", 1, "04A1B2C3D4E580")).state)
        assertEquals("QUEUED", api.stopSession(1).state)
        assertEquals(listOf(2026, 2025), api.statisticsYears())
        assertEquals(8.0, api.statistics("consumption", 2026).total, 0.0)
        assertEquals("year=2026", fixtures.calls("GET", "statistics/consumption").single().query)
    }

    @Test
    fun readsBadgesCarsAndAutoCharge() = runBlocking {
        assertEquals(2, api.tokens().size)
        assertTrue(api.cardOffer().available)
        assertEquals(CardOrderState.PendingPayment, api.cardOrders().single().state)
        assertEquals("CARTE-2", api.activateCard("CARTE-2").visualId)
        assertEquals("a.b.c", api.googleWalletPass("04A1B2C3D4E580").jwt)
        assertEquals("Zoé", api.cars().single().name)
        val car = RecordInput("Zoé", CarData(userId = UUID.randomUUID(), make = "Renault"), null)
        assertEquals(0, api.createCar(car).revision)
        assertFalse(fixtures.calls("POST", "account/cars").single().body.contains("userId"))
        assertEquals(1, api.updateCar(carId, car.copy(revision = 0)).revision)
        api.deleteCar(carId)
        assertEquals("R135", api.carCatalog("zoe").items.single().data.version)
        assertEquals("limit=50&search=zoe", fixtures.calls("GET", "account/car-catalog").single().query)
        assertTrue(api.autoChargeCandidates().isEmpty())
        assertTrue(api.vehicleIds(carId).isEmpty())
        assertEquals("VID:ABCDEF", api.enrollAutoCharge(carId, 3).uid)
        assertTrue(fixtures.calls("POST", "account/cars/$carId/autocharge").single().body.contains("\"transactionId\":3"))
        api.revokeAutoCharge(carId, "VID:ABCDEF")
        assertEquals(1, fixtures.calls("DELETE", "account/cars/$carId/autocharge/VID:ABCDEF").size)
    }

    @Test
    fun readsNotificationsBillingAndDevices() = runBlocking {
        assertEquals("Votre session de recharge a commencé", api.notifications().items.single().subject)
        assertTrue(api.notificationTypes().any { it.type == "END_OF_CHARGE" })
        val preferences = api.notificationPreferences()
        assertTrue(preferences.push)
        api.updateNotificationPreferences(preferences.copy(email = false))
        assertTrue(fixtures.calls("PUT", "account/notifications").single().body.contains("\"email\":false"))
        api.reportProblem(ErrorReportInput("Câble abîmé", "Le câble est coupé", "CONTRAT-1", 1))
        api.registerDevice(PushDeviceInput("token", "ANDROID", "sandbox", "fr"))
        assertTrue(api.invoices().items.isEmpty())
        assertEquals("https://invoices.stripe.com/i/test", api.invoiceDownload(order).url)
        assertEquals(listOf("pm_1", "pm_2"), api.paymentMethods().map { it.id })
        assertTrue(api.paymentMethodCheckout().url.startsWith("https://"))
        api.setDefaultPaymentMethod("pm_2")
        api.deletePaymentMethod("pm_2")
        assertEquals(1, fixtures.calls("POST", "account/payment-methods/pm_2/default").size)
        assertEquals(1, fixtures.calls("DELETE", "account/payment-methods/pm_2").size)
        assertEquals("limit=50&offset=0", fixtures.calls("GET", "invoices").single().query)
    }

    @Test
    fun runsTheGuestPaymentFlow() = runBlocking {
        val portal = api.portal(FixtureServer.WORKSPACE, evse)
        assertEquals(500, portal.minimumCreditMinor)
        val checkout = api.checkout(portal.workspaceSlug, portal.id, CheckoutRequest(UUID.randomUUID(), "secret", 2000))
        assertEquals(order, checkout.orderId)
        assertEquals(PaymentState.PendingPayment, api.guestSession(order, "secret").state)
        api.stopGuestSession(order, "secret")
        val stop = fixtures.calls("POST", "public/sessions/$order/stop").single()
        assertEquals("secret", stop.headers["X-Charge-Token"])
    }

    @Test
    fun reportsServerFailuresWithoutASession() {
        fixtures.respond("GET", "account/sites", "<html>Bad gateway</html>", 502)
        val failure = runCatching { runBlocking { api.sites() } }.exceptionOrNull() as ApiError.Server
        assertEquals(502, failure.status)
        assertNull(failure.detail)
        fixtures.fail("GET", "auth/me", 401, "Identifiants incorrects.")
        val unauthenticated = runCatching { runBlocking { api.me() } }.exceptionOrNull() as ApiError.Server
        assertEquals("Identifiants incorrects.", unauthenticated.detail)
    }

    @Test
    fun anUnreachableServerIsOffline() {
        val closed = EvServerApi(ApiClient("http://127.0.0.1:1/"))
        assertEquals(ApiError.Offline, runCatching { runBlocking { closed.configuration() } }.exceptionOrNull())
    }
}
