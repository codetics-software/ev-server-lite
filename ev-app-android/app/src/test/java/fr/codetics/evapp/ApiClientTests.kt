// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import fr.codetics.evapp.core.ApiClient
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.core.EvServerApi
import fr.codetics.evapp.models.PushDeviceInput
import fr.codetics.evapp.models.SessionStartInput
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTests {
    private lateinit var server: MockWebServer
    private lateinit var api: EvServerApi

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val csrf = """{"token":"tok","headerName":"X-XSRF-TOKEN"}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = EvServerApi(ApiClient(server.url("/").toString()))
    }

    @After
    fun stop() {
        server.close()
    }

    @Test
    fun postSendsCsrfAndBearerThenEncodesCamelCase() = runBlocking {
        api.client.bearerToken = "secret"
        server.enqueue(json(csrf))
        server.enqueue(json("""{"id":"5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10","state":"QUEUED"}"""))
        assertEquals("QUEUED", api.startSession(SessionStartInput("A", 1, "B")).state)
        assertEquals("/api/v1/auth/csrf", server.takeRequest().url.encodedPath)
        val post = server.takeRequest()
        assertEquals("tok", post.headers["X-XSRF-TOKEN"])
        assertEquals("Bearer secret", post.headers["Authorization"])
        assertTrue(post.body!!.utf8().contains("\"stationId\":\"A\""))
    }

    @Test
    fun retriesOnceWithAFreshCsrfTokenAfterForbidden() = runBlocking {
        server.enqueue(json(csrf))
        server.enqueue(json("{}", 403))
        server.enqueue(json(csrf))
        server.enqueue(json("""{"success":true}"""))
        api.registerDevice(PushDeviceInput("t", "ANDROID", "production"))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun mapsProblemDetailsAndExpiredSessions() {
        server.enqueue(json("""{"status":404,"detail":"Borne introuvable."}""", 404))
        val notFound = assertThrows(ApiError.Server::class.java) { runBlocking { api.station("X") } }
        assertEquals("Borne introuvable.", notFound.detail)
        api.client.bearerToken = "expired"
        server.enqueue(json("{}", 401))
        assertThrows(ApiError.Unauthorized::class.java) { runBlocking { api.me() } }
    }

    @Test
    fun sendsTheAppLanguageAndReadsProblemCodes() {
        val spanish = EvServerApi(ApiClient(server.url("/").toString(), language = { "es" }))
        server.enqueue(json("""{"status":401,"detail":"Verifique su dirección de correo electrónico antes de iniciar sesión.","code":"email_not_verified"}""", 401))
        val failure = assertThrows(ApiError.Server::class.java) { runBlocking { spanish.me() } }
        assertEquals("email_not_verified", failure.code)
        assertEquals("es", server.takeRequest().headers["Accept-Language"])
        server.enqueue(json("{}"))
        runCatching { runBlocking { api.workspace() } }
        assertTrue(server.takeRequest().headers["Accept-Language"] in setOf("en", "fr", "es"))
    }

    @Test
    fun malformedResponsesAreReportedAndGuestTokenIsSent() = runBlocking {
        server.enqueue(json("not json"))
        val failure = runCatching { api.configuration() }.exceptionOrNull()
        assertEquals(ApiError.InvalidResponse, failure)
        val id = java.util.UUID.fromString("5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10")
        server.enqueue(json("""{"id":"$id","state":"CHARGING","prepaidMinor":2000,"refundedMinor":0,"currency":"EUR"}"""))
        assertNull(api.guestSession(id, "guest-token").billedMinor)
        server.takeRequest()
        assertEquals("guest-token", server.takeRequest().headers["X-Charge-Token"])
    }

    @Test
    fun fcmTokensWithColonsReachTheDeleteRoute() = runBlocking {
        server.enqueue(json(csrf))
        server.enqueue(json("""{"success":true}"""))
        api.unregisterDevice("fXyZ:APA91b_Token-Value")
        server.takeRequest()
        assertEquals("/api/v1/account/devices/fXyZ:APA91b_Token-Value", server.takeRequest().url.encodedPath)
    }
}
