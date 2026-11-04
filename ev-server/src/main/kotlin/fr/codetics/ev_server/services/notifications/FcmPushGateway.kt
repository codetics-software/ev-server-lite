// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.notifications

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.models.entities.PushDeviceEntity
import fr.codetics.ev_server.models.entities.PushMessageEntity
import fr.codetics.ev_server.services.google.GoogleServiceAccount
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class FcmPushGateway(
    properties: AppProperties,
    private val json: ObjectMapper,
    private val clock: Clock,
) : PushGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val settings = properties.push.fcm
    private val client: HttpClient =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build()
    private val account by lazy { GoogleServiceAccount.read(settings.serviceAccount, json) }

    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenAt: Instant = Instant.EPOCH

    override val platform = PushPlatform.ANDROID

    override val configured: Boolean
        get() = settings.serviceAccount.isNotBlank()

    val projectId: String
        get() = settings.projectId.ifBlank { requireNotNull(account.projectId) { "Le projet Firebase n’est pas configuré." } }

    override fun send(device: PushDeviceEntity, message: PushMessageEntity): PushResult =
        try {
            val request =
                HttpRequest.newBuilder(URI("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("authorization", "Bearer ${token()}")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(device, message)))
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 401) accessToken = null
            interpret(response.statusCode(), response.body())
        } catch (error: IOException) {
            log.warn("FCM injoignable : {}", error.message)
            PushResult.RETRY
        } catch (error: IllegalStateException) {
            log.warn("FCM non autorisé : {}", error.message)
            PushResult.RETRY
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            PushResult.RETRY
        }

    fun payload(device: PushDeviceEntity, message: PushMessageEntity): String {
        val data =
            buildMap<String, String> {
                put("type", message.type)
                message.transactionId?.let { put("transaction_id", it.toString()) }
            }
        return json.writeValueAsString(
            mapOf(
                "message" to
                    mapOf(
                        "token" to device.token,
                        "notification" to mapOf("title" to message.title, "body" to message.body.take(BODY_LIMIT)),
                        "data" to data,
                        "android" to
                            mapOf(
                                "priority" to "HIGH",
                                "notification" to
                                    mapOf(
                                        "channel_id" to CHANNEL,
                                        "tag" to (message.transactionId?.let { "session-$it" } ?: message.type),
                                    ),
                            ),
                    )
            )
        )
    }

    fun interpret(status: Int, body: String): PushResult {
        val code =
            runCatching {
                    json.readTree(body).path("error").path("details").firstNotNullOfOrNull { detail ->
                        detail.path("errorCode").asText().ifBlank { null }
                    }
                }
                .getOrNull()
        return when {
            status in 200..299 -> PushResult.DELIVERED
            status == 404 || code == "UNREGISTERED" -> PushResult.INVALID_DEVICE
            code == "SENDER_ID_MISMATCH" -> PushResult.INVALID_DEVICE
            status == 400 && code == "INVALID_ARGUMENT" && body.contains("registration token") -> PushResult.INVALID_DEVICE
            else -> {
                log.warn("FCM a refusé une notification : {} {}", status, code)
                PushResult.RETRY
            }
        }
    }

    fun token(): String {
        val now = Instant.now(clock)
        accessToken?.takeIf { Duration.between(accessTokenAt, now) < TOKEN_LIFETIME }?.let {
            return it
        }
        return account.accessToken(SCOPE, now, client).also {
            accessToken = it
            accessTokenAt = now
        }
    }

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        const val CHANNEL = "charging"
        const val BODY_LIMIT = 400
        val TOKEN_LIFETIME: Duration = Duration.ofMinutes(45)
    }
}
