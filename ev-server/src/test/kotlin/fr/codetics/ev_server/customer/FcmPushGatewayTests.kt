// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.customer

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.models.entities.PushDeviceEntity
import fr.codetics.ev_server.models.entities.PushMessageEntity
import fr.codetics.ev_server.services.notifications.FcmPushGateway
import fr.codetics.ev_server.services.notifications.PushPlatform
import fr.codetics.ev_server.services.notifications.PushResult
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class FcmPushGatewayTests {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val mapper = JsonMapper.builder().build()
    private val serviceAccount =
        mapper.writeValueAsString(
            mapOf(
                "project_id" to "ev-demo",
                "client_email" to "fcm@ev-demo.iam.gserviceaccount.com",
                "token_uri" to "https://oauth2.googleapis.com/token",
                "private_key" to
                    "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n",
            )
        )
    private val gateway =
        FcmPushGateway(
            AppProperties(push = AppProperties.Push(fcm = AppProperties.Push.Fcm(serviceAccount = serviceAccount))),
            mapper,
            Clock.systemUTC(),
        )

    @Test
    fun `le compte de service est lu et configure la plateforme Android`() {
        assertTrue(gateway.configured)
        assertEquals(PushPlatform.ANDROID, gateway.platform)
        assertEquals("ev-demo", gateway.projectId)
        assertFalse(FcmPushGateway(AppProperties(), mapper, Clock.systemUTC()).configured)
    }

    @Test
    fun `l assertion OAuth est un JWT RS256 verifiable`() {
        val account = fr.codetics.ev_server.services.google.GoogleServiceAccount.read(serviceAccount, mapper)
        val parts = account.assertion("scope-test", Instant.ofEpochSecond(1000)).split('.')
        assertEquals(3, parts.size)
        val decoder = Base64.getUrlDecoder()
        assertEquals("RS256", mapper.readTree(decoder.decode(parts[0])).path("alg").asText())
        val claims = mapper.readTree(decoder.decode(parts[1]))
        assertEquals("fcm@ev-demo.iam.gserviceaccount.com", claims.path("iss").asText())
        assertEquals("scope-test", claims.path("scope").asText())
        assertEquals(1000, claims.path("iat").asInt())
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(pair.public)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray())
        assertTrue(verifier.verify(decoder.decode(parts[2])))
    }

    @Test
    fun `la charge utile porte le jeton, le canal et la session en texte`() {
        val token = "fXyZ:APA91b_token-with-Mixed-Case"
        val message =
            mapper
                .readTree(
                    gateway.payload(
                        PushDeviceEntity(token = token, platform = PushPlatform.ANDROID),
                        PushMessageEntity(type = "END_OF_CHARGE", title = "Charge terminée", body = "x".repeat(900), transactionId = 42),
                    )
                )
                .path("message")
        assertEquals(token, message.path("token").asText())
        assertEquals("Charge terminée", message.path("notification").path("title").asText())
        assertEquals(400, message.path("notification").path("body").asText().length)
        assertEquals("END_OF_CHARGE", message.path("data").path("type").asText())
        assertTrue(message.path("data").path("transaction_id").isString)
        assertEquals("42", message.path("data").path("transaction_id").asText())
        assertEquals("HIGH", message.path("android").path("priority").asText())
        assertEquals("charging", message.path("android").path("notification").path("channel_id").asText())
        assertEquals("session-42", message.path("android").path("notification").path("tag").asText())
    }

    @Test
    fun `les reponses FCM sont interpretees`() {
        fun error(status: Int, code: String, text: String = "") =
            """{"error":{"code":$status,"message":"$text","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"$code"}]}}"""
        assertEquals(PushResult.DELIVERED, gateway.interpret(200, """{"name":"projects/x/messages/1"}"""))
        assertEquals(PushResult.INVALID_DEVICE, gateway.interpret(404, error(404, "UNREGISTERED")))
        assertEquals(PushResult.INVALID_DEVICE, gateway.interpret(403, error(403, "SENDER_ID_MISMATCH")))
        assertEquals(
            PushResult.INVALID_DEVICE,
            gateway.interpret(400, error(400, "INVALID_ARGUMENT", "The registration token is not a valid FCM registration token")),
        )
        assertEquals(PushResult.RETRY, gateway.interpret(400, error(400, "INVALID_ARGUMENT", "Invalid channel")))
        assertEquals(PushResult.RETRY, gateway.interpret(429, error(429, "QUOTA_EXCEEDED")))
        assertEquals(PushResult.RETRY, gateway.interpret(503, ""))
    }
}
