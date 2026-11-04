// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.customer

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.models.entities.PushMessageEntity
import fr.codetics.ev_server.services.notifications.ApnsPushGateway
import fr.codetics.ev_server.services.notifications.PushResult
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class ApnsPushGatewayTests {
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val mapper = JsonMapper.builder().build()
    private val gateway =
        ApnsPushGateway(
            AppProperties(
                push =
                    AppProperties.Push(
                        AppProperties.Push.Apns(
                            key = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----",
                            keyId = "KEY1234567",
                            teamId = "TEAM123456",
                            topic = "fr.codetics.EV-App",
                        )
                    )
            ),
            mapper,
            Clock.systemUTC(),
        )

    @Test
    fun `le jeton est un JWT ES256 verifiable`() {
        assertTrue(gateway.configured)
        val parts = gateway.token().split('.')
        assertEquals(3, parts.size)
        val decoder = Base64.getUrlDecoder()
        val header = mapper.readTree(decoder.decode(parts[0]))
        assertEquals("ES256", header.path("alg").asText())
        assertEquals("KEY1234567", header.path("kid").asText())
        assertEquals("TEAM123456", mapper.readTree(decoder.decode(parts[1])).path("iss").asText())
        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format")
        verifier.initVerify(pair.public)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray())
        assertTrue(verifier.verify(decoder.decode(parts[2])))
        assertEquals(gateway.token(), gateway.token())
    }

    @Test
    fun `la charge utile porte le type et la session`() {
        val payload =
            mapper.readTree(
                gateway.payload(
                    PushMessageEntity(type = "END_OF_CHARGE", title = "Charge terminée", body = "x".repeat(900), transactionId = 42)
                )
            )
        assertEquals("Charge terminée", payload.path("aps").path("alert").path("title").asText())
        assertEquals(400, payload.path("aps").path("alert").path("body").asText().length)
        assertEquals("session-42", payload.path("aps").path("thread-id").asText())
        assertEquals("END_OF_CHARGE", payload.path("type").asText())
        assertEquals(42, payload.path("transaction_id").asInt())
    }

    @Test
    fun `les reponses APNs sont interpretees`() {
        assertEquals(PushResult.DELIVERED, gateway.interpret(200, ""))
        assertEquals(PushResult.INVALID_DEVICE, gateway.interpret(410, """{"reason":"Unregistered"}"""))
        assertEquals(PushResult.INVALID_DEVICE, gateway.interpret(400, """{"reason":"BadDeviceToken"}"""))
        assertEquals(PushResult.RETRY, gateway.interpret(429, """{"reason":"TooManyRequests"}"""))
        assertEquals(PushResult.RETRY, gateway.interpret(503, ""))
    }
}
