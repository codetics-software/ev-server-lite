// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.notifications

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.entities.PushDeviceEntity
import fr.codetics.ev_server.models.entities.PushMessageEntity
import fr.codetics.ev_server.models.notifications.PushDeviceInput
import fr.codetics.ev_server.repositories.PushDeviceRepository
import fr.codetics.ev_server.repositories.PushMessageRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class PushService(
    private val db: Database,
    private val devices: PushDeviceRepository,
    private val messages: PushMessageRepository,
    private val gateways: List<PushGateway>,
    private val clock: Clock,
) {
    val configured: Boolean
        get() = configured(PushPlatform.IOS)

    val androidConfigured: Boolean
        get() = configured(PushPlatform.ANDROID)

    private fun configured(platform: String) = gateway(platform) != null

    private fun gateway(platform: String) = gateways.firstOrNull { it.platform == platform && it.configured }

    private fun normalize(token: String, platform: String) = if (platform == PushPlatform.IOS) token.lowercase() else token

    fun register(user: AppPrincipal, input: PushDeviceInput): MutationResponse {
        val format = TOKENS[input.platform]
        ensure(format != null) { "Plateforme non prise en charge." }
        ensure(input.token.matches(format!!)) { "Identifiant d’appareil invalide." }
        ensure(input.environment in ENVIRONMENTS) { "Environnement de notification inconnu." }
        ensure(input.locale == null || input.locale.length <= 20) { "Langue invalide." }
        val token = normalize(input.token, input.platform)
        return db.atomic {
            val now = Instant.now(clock)
            val device = devices.findById(token).orElse(null) ?: PushDeviceEntity(token = token, createdAt = now)
            device.tenantId = user.tenantId
            device.userId = user.id
            device.platform = input.platform
            device.environment = input.environment
            device.locale = input.locale
            device.updatedAt = now
            devices.save(device)
            MutationResponse()
        }
    }

    fun unregister(user: AppPrincipal, token: String): MutationResponse =
        db.atomic {
            listOf(token, token.lowercase())
                .firstNotNullOfOrNull { devices.findById(it).orElse(null) }
                ?.takeIf { it.userId == user.id }
                ?.let(devices::delete)
            MutationResponse()
        }

    fun forget(user: UUID) {
        devices.deleteAllByUserId(user)
        messages.deleteAllByUserId(user)
    }

    fun deliverable(tenant: UUID, user: UUID) =
        devices.findAllByTenantIdAndUserId(tenant, user).any { configured(it.platform) }

    fun enqueue(tenant: UUID, user: UUID, type: String, title: String, body: String, transactionId: Int?) {
        messages.save(
            PushMessageEntity(
                tenantId = tenant,
                userId = user,
                type = type,
                title = title.take(200),
                body = body.take(1000),
                transactionId = transactionId,
                createdAt = Instant.now(clock),
            )
        )
    }

    @Scheduled(fixedDelay = 5000)
    fun deliver() {
        if (gateways.none { it.configured }) return
        db.atomic {
            messages
                .findAllBySentAtIsNullAndAttemptsLessThanOrderByCreatedAt(MAXIMUM_ATTEMPTS, PageRequest.of(0, BATCH))
                .mapNotNull { db.lock<PushMessageEntity>(it.id) }
                .filter { it.sentAt == null }
                .forEach(::send)
            messages.deleteAllByCreatedAtBefore(Instant.now(clock).minus(RETENTION))
        }
    }

    private fun send(message: PushMessageEntity) {
        val targets = devices.findAllByTenantIdAndUserId(message.tenantId, message.userId)
        val results =
            targets.mapNotNull { device ->
                val gateway = gateway(device.platform) ?: return@mapNotNull null
                val result = gateway.send(device, message)
                if (result == PushResult.INVALID_DEVICE) devices.delete(device)
                result
            }
        if (results.any { it == PushResult.RETRY }) {
            message.attempts += 1
        } else {
            message.sentAt = Instant.now(clock)
            message.title = ""
            message.body = ""
        }
        messages.save(message)
    }

    private companion object {
        val TOKENS =
            mapOf(
                PushPlatform.IOS to Regex("[0-9a-fA-F]{32,200}"),
                PushPlatform.ANDROID to Regex("[A-Za-z0-9_:-]{32,1024}"),
            )
        val ENVIRONMENTS = setOf("sandbox", "production")
        const val MAXIMUM_ATTEMPTS = 5
        const val BATCH = 50
        val RETENTION: Duration = Duration.ofDays(7)
    }
}
