// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.notifications

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.models.entities.PushDeviceEntity
import fr.codetics.ev_server.models.entities.PushMessageEntity
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApnsPushGateway(
    properties: AppProperties,
    private val json: ObjectMapper,
    private val clock: Clock,
) : PushGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val settings = properties.push.apns
    override val platform = PushPlatform.IOS
    private val client: HttpClient =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build()
    private val key: PrivateKey? by lazy { settings.key.takeIf(String::isNotBlank)?.let(::readKey) }

    @Volatile private var bearer: String? = null
    @Volatile private var bearerAt: Instant = Instant.EPOCH

    override val configured: Boolean
        get() =
            settings.key.isNotBlank() &&
                settings.keyId.isNotBlank() &&
                settings.teamId.isNotBlank() &&
                settings.topic.isNotBlank()

    override fun send(device: PushDeviceEntity, message: PushMessageEntity): PushResult {
        val host = if (device.environment == "sandbox") SANDBOX else PRODUCTION
        val request =
            HttpRequest.newBuilder(URI("https://$host/3/device/${device.token}"))
                .timeout(Duration.ofSeconds(15))
                .header("authorization", "bearer ${token()}")
                .header("apns-topic", settings.topic)
                .header("apns-push-type", "alert")
                .header("apns-priority", "10")
                .header("apns-collapse-id", message.id.toString().take(64))
                .POST(HttpRequest.BodyPublishers.ofString(payload(message)))
                .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            interpret(response.statusCode(), response.body())
        } catch (error: IOException) {
            log.warn("APNs injoignable : {}", error.message)
            PushResult.RETRY
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            PushResult.RETRY
        }
    }

    fun payload(message: PushMessageEntity): String {
        val payload =
            buildMap<String, Any?> {
                put(
                    "aps",
                    mapOf(
                        "alert" to mapOf("title" to message.title, "body" to message.body.take(BODY_LIMIT)),
                        "sound" to "default",
                        "thread-id" to (message.transactionId?.let { "session-$it" } ?: message.type),
                    ),
                )
                put("type", message.type)
                message.transactionId?.let { put("transaction_id", it) }
            }
        return json.writeValueAsString(payload)
    }

    fun interpret(status: Int, body: String): PushResult {
        val reason = runCatching { json.readTree(body).path("reason").asText() }.getOrDefault("")
        return when {
            status in 200..299 -> PushResult.DELIVERED
            status == 410 -> PushResult.INVALID_DEVICE
            status == 400 && reason in setOf("BadDeviceToken", "DeviceTokenNotForTopic") -> PushResult.INVALID_DEVICE
            else -> {
                log.warn("APNs a refusé une notification : {} {}", status, reason)
                PushResult.RETRY
            }
        }
    }

    fun token(): String {
        val now = Instant.now(clock)
        bearer?.takeIf { Duration.between(bearerAt, now) < TOKEN_LIFETIME }?.let {
            return it
        }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"ES256","kid":"${settings.keyId}"}""".toByteArray())
        val claims = encoder.encodeToString("""{"iss":"${settings.teamId}","iat":${now.epochSecond}}""".toByteArray())
        val signer = Signature.getInstance("SHA256withECDSAinP1363Format")
        signer.initSign(requireNotNull(key) { "La clé APNs n’est pas configurée." })
        signer.update("$header.$claims".toByteArray())
        val token = "$header.$claims.${encoder.encodeToString(signer.sign())}"
        bearer = token
        bearerAt = now
        return token
    }

    private fun readKey(value: String): PrivateKey {
        val pem = if (value.contains("BEGIN")) value else Files.readString(Path.of(value))
        val encoded =
            pem.replace("\\n", "\n")
                .lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString("")
                .filterNot(Char::isWhitespace)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))
    }

    private companion object {
        const val PRODUCTION = "api.push.apple.com"
        const val SANDBOX = "api.sandbox.push.apple.com"
        const val BODY_LIMIT = 400
        val TOKEN_LIFETIME: Duration = Duration.ofMinutes(50)
    }
}
