package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.I18n
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.core.stationSecret
import fr.codetics.ev_server.models.commands.CommandState
import fr.codetics.ev_server.models.entities.CommandEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.notifications.NotificationType
import fr.codetics.ev_server.models.stations.OcppSecurityCapabilities
import fr.codetics.ev_server.models.stations.ProvisionedStation
import fr.codetics.ev_server.models.stations.StationResponse
import fr.codetics.ev_server.models.stations.OcppSettingsInput
import fr.codetics.ev_server.models.stations.SecurityChange
import fr.codetics.ev_server.models.stations.SecurityProfileInput
import fr.codetics.ev_server.ocpp.ChargerCertificates
import fr.codetics.ev_server.ocpp.CommandCompleted
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.StationIdentity
import fr.codetics.ev_server.ocpp.OcppGateway
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.services.notifications.NotificationService
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class StationSecurityService(
    private val db: Database,
    private val stations: StationRepository,
    private val stationEvents: StationEventRepository,
    private val commands: CommandRepository,
    private val queue: OcppCommandQueue,
    private val gateway: OcppGateway,
    private val passwords: PasswordEncoder,
    private val certificates: ChargerCertificates,
    private val properties: AppProperties,
    private val stationService: StationService,
    private val notifications: NotificationService,
) {
    fun capabilities() =
        OcppSecurityCapabilities(
            allowUnauthenticated = properties.ocpp.allowUnauthenticated,
            clientCertificateAuthority = certificates.clientAuthorityConfigured,
            clientCertificateHeader = certificates.clientCertificateHeader.isNotEmpty(),
            trustedProxies = certificates.proxiesConfigured,
            certificateSigning = certificates.signingConfigured,
            defaultHeartbeatInterval = properties.ocpp.heartbeatInterval(),
            ocppPublicUrl = properties.ocpp.publicUrl.trim().trimEnd('/').ifBlank { null },
        )

    fun changeProfile(user: AppPrincipal, stationId: String, input: SecurityProfileInput): SecurityChange {
        ensure(input.securityProfile in 0..3) { msg("security.profile_range") }
        conflict(input.securityProfile != 0 || properties.ocpp.allowUnauthenticated) {
            msg("security.profile_zero_disabled")
        }
        val pinned =
            input.pinnedCertificateFingerprint?.let(ChargerCertificates::normalizeFingerprint)?.ifBlank { null }
        ensure(pinned == null || pinned.matches(Regex("[0-9a-f]{64}"))) {
            msg("security.fingerprint")
        }

        val identity = StationIdentity(user.tenantId, stationId)
        val connected = gateway.isConnected(identity)
        val result =
            db.atomic {
                val station = locked(user, stationId)
                val previousPin = station.pinnedCertificateFingerprint
                val from = station.securityProfile
                val target = input.securityProfile
                val raising = target > from
                var command: UUID? = null
                val message: String

                if (raising && target == 3) {
                    conflict(pinned != null || certificates.clientAuthorityConfigured) {
                        msg("security.no_client_ca")
                    }
                }
                station.pinnedCertificateFingerprint = pinned
                if (pinned != previousPin) {
                    db.audit(
                        user.tenantId,
                        user.id.toString(),
                        if (pinned == null) "STATION_CERTIFICATE_UNPIN" else "STATION_CERTIFICATE_PIN",
                        "$stationId/${pinned ?: previousPin}",
                    )
                }

                if (target == from) {
                    message = msg("security.saved")
                } else if (raising && input.sendToStation && target > 0) {
                    conflict(connected) { msg("security.must_be_connected_profile") }
                    if (target >= 2) {
                        conflict((station.connectionSecurityProfile ?: 0) >= 2) {
                            msg("security.must_use_tls")
                        }
                    }
                    if (target == 3) {
                        conflict(
                            pinned != null ||
                                station.issuedCertificateFingerprint != null ||
                                station.connectionSecurityProfile == 3
                        ) {
                            msg("security.no_client_certificate")
                        }
                    }
                    station.requestedSecurityProfile = target
                    command =
                        queue.command(
                            user.tenantId,
                            stationId,
                            "ChangeConfiguration",
                            db.json.valueToTree(mapOf("key" to SECURITY_PROFILE_KEY, "value" to target.toString())),
                        )
                    message =
                        msg("security.profile_requested", target)
                } else {
                    station.securityProfile = target
                    station.requestedSecurityProfile = null
                    message =
                        if (raising) {
                            msg("security.profile_applied", target)
                        } else {
                            msg("security.profile_lowered", target)
                        }
                }
                stations.save(station)
                val outcome =
                    when {
                        target == from -> "inchangé"
                        command != null -> "envoyé à la borne"
                        raising -> "appliqué"
                        else -> "abaissé, à reconfigurer sur place"
                    }
                event(station, "SecurityProfileChange", "$from → $target ($outcome)")
                db.audit(
                    user.tenantId,
                    user.id.toString(),
                    "STATION_SECURITY_PROFILE",
                    "$stationId/$from→$target/$outcome",
                )
                if (target == 0 && from != 0) {
                    db.audit(user.tenantId, user.id.toString(), "STATION_SECURITY_PROFILE_ZERO", stationId)
                }
                Triple(station, command, message)
            }

        val station = result.first
        if (station.requestedSecurityProfile == null && (station.connectionSecurityProfile ?: 0) < station.securityProfile) {
            gateway.disconnect(identity)
        }
        return SecurityChange(stationService.find(user.tenantId, stationId), result.second, result.third)
    }

    fun rotateRemotely(user: AppPrincipal, stationId: String): ProvisionedStation {
        conflict(gateway.isConnected(StationIdentity(user.tenantId, stationId))) {
            msg("security.must_be_connected_password")
        }
        val secret = stationSecret()
        return db.atomic {
            val station = locked(user, stationId)
            station.pendingSecretHash = requireNotNull(passwords.encode(secret))
            stations.save(station)
            queue.command(
                user.tenantId,
                stationId,
                "ChangeConfiguration",
                db.json.valueToTree(mapOf("key" to AUTHORIZATION_KEY, "value" to secret)),
            )
            event(station, "PasswordRotationRequested")
            db.audit(user.tenantId, user.id.toString(), "STATION_SECRET_REMOTE_ROTATE", stationId)
            ProvisionedStation(
                stationId,
                secret,
                msg("security.password_rotated"),
            )
        }
    }

    fun cancelRotation(user: AppPrincipal, stationId: String) =
        db.atomic {
            val station = locked(user, stationId)
            val last =
                commands
                    .findAllByTenantIdAndStationIdAndActionOrderByCreatedAtDesc(
                        user.tenantId,
                        stationId,
                        "ChangeConfiguration",
                        PageRequest.of(0, 100),
                    )
                    .firstOrNull { db.decode(it.request).path("key").asText().equals(AUTHORIZATION_KEY, true) }
            if (last != null) {
                val delivered =
                    last.state == CommandState.SENT ||
                        (last.state == CommandState.SUCCEEDED &&
                            last.result?.let { db.decode(it).path("status").asText() } in
                                setOf("Accepted", "RebootRequired"))
                conflict(!delivered) {
                    msg("security.password_maybe_received")
                }
                db.lock<CommandEntity>(last.id)?.takeIf { it.state == CommandState.QUEUED }?.let { queued ->
                    queued.state = CommandState.CANCELLED
                    queued.completedAt = Instant.now()
                    queued.request = db.encode(mapOf("key" to AUTHORIZATION_KEY, "value" to REDACTED))
                    commands.save(queued)
                }
            }
            station.pendingSecretHash = null
            stations.save(station)
            event(station, "PasswordRotationCancelled")
            db.audit(user.tenantId, user.id.toString(), "STATION_SECRET_ROTATE_CANCEL", stationId)
            stationService.find(user.tenantId, stationId)
        }

    fun revokePreviousPassword(user: AppPrincipal, stationId: String): StationResponse {
        db.atomic {
            val station = locked(user, stationId)
            conflict(station.pendingSecretHash != null) { msg("security.no_pending_password") }
            station.secretHash = requireNotNull(station.pendingSecretHash)
            station.pendingSecretHash = null
            stations.save(station)
            event(station, "PreviousPasswordRevoked")
            db.audit(user.tenantId, user.id.toString(), "STATION_SECRET_REVOKE_PREVIOUS", stationId)
        }
        gateway.disconnect(StationIdentity(user.tenantId, stationId))
        return stationService.find(user.tenantId, stationId)
    }

    fun revokeCertificate(user: AppPrincipal, stationId: String): StationResponse {
        db.atomic {
            val station = locked(user, stationId)
            val previous = station.issuedCertificateFingerprint ?: station.pinnedCertificateFingerprint
            station.issuedCertificateFingerprint = null
            station.pinnedCertificateFingerprint = null
            stations.save(station)
            event(station, "CertificateRevoked", previous)
            db.audit(user.tenantId, user.id.toString(), "STATION_CERTIFICATE_REVOKE", "$stationId/$previous")
        }
        gateway.disconnect(StationIdentity(user.tenantId, stationId))
        return stationService.find(user.tenantId, stationId)
    }

    fun certificateCommand(user: AppPrincipal, stationId: String, action: String, payload: Map<String, Any?>): UUID {
        ensure(action in ADMIN_CERTIFICATE_ACTIONS) { msg("security.unknown_certificate_action") }
        conflict(action != "ExtendedTriggerMessage" || certificates.signingConfigured) {
            msg("security.no_signing_ca")
        }
        stationService.station(user.tenantId, stationId)
        val request =
            if (action == "ExtendedTriggerMessage") mapOf("requestedMessage" to "SignChargePointCertificate") else payload
        val id = queue.command(user.tenantId, stationId, action, db.json.valueToTree(request))
        db.audit(user.tenantId, user.id.toString(), "STATION_CERTIFICATE_$action", stationId)
        return id
    }

    fun changeSettings(user: AppPrincipal, stationId: String, input: OcppSettingsInput): SecurityChange {
        ensure(input.heartbeatInterval == null || input.heartbeatInterval in 10..300) {
            msg("security.heartbeat_range")
        }
        val vendors = input.dataTransferVendors.map(String::trim).filter(String::isNotBlank).distinct()
        ensure(vendors.all { it.length <= 255 && !it.contains(',') }) { msg("security.invalid_vendor") }
        ensure(vendors.joinToString(",").length <= 1000) { msg("security.vendors_too_long") }

        val command =
            db.atomic {
                val station = locked(user, stationId)
                val changed = station.heartbeatInterval != input.heartbeatInterval
                station.heartbeatInterval = input.heartbeatInterval
                station.dataTransferVendors = vendors.joinToString(",")
                stations.save(station)
                db.audit(user.tenantId, user.id.toString(), "STATION_OCPP_SETTINGS", stationId)
                if (changed) {
                    queue.command(
                        user.tenantId,
                        stationId,
                        "ChangeConfiguration",
                        db.json.valueToTree(
                            mapOf(
                                "key" to "HeartbeatInterval",
                                "value" to (input.heartbeatInterval ?: properties.ocpp.heartbeatInterval()).toString(),
                            )
                        ),
                    )
                } else {
                    null
                }
            }
        return SecurityChange(stationService.find(user.tenantId, stationId), command, msg("security.ocpp_saved"))
    }

    @EventListener
    fun completed(event: CommandCompleted) {
        val command = event.command
        when (command.action) {
            "ChangeConfiguration" -> {
                val request = db.decode(command.request)
                val key = request.path("key").asText()
                if (!protectedKey(key)) return
                val status = if (event.success) event.result.path("status").asText() else null
                val accepted = status in setOf("Accepted", "RebootRequired")
                val station = stations.findById(StationKey(command.tenantId, command.stationId)).orElse(null) ?: return

                when (key) {
                    SECURITY_PROFILE_KEY -> {
                        val requested = request.path("value").asText().toIntOrNull()
                        if (!accepted && station.requestedSecurityProfile == requested) {
                            station.requestedSecurityProfile = null
                        }
                        event(station, "SecurityProfile${status ?: command.state.name}", requested?.toString())
                        db.audit(
                            command.tenantId,
                            "station:${command.stationId}",
                            "STATION_SECURITY_PROFILE",
                            "${command.stationId}/$requested/${status ?: command.state.name}",
                        )
                    }
                    AUTHORIZATION_KEY -> {
                        event(station, "PasswordRotation${status ?: command.state.name}")
                        redact(command)
                    }
                }
                stations.save(station)
            }
            "CertificateSigned", "InstallCertificate", "DeleteCertificate" -> {
                val station = stations.findById(StationKey(command.tenantId, command.stationId)).orElse(null) ?: return
                val status = if (event.success) event.result.path("status").asText() else command.state.name
                stationEvents.save(
                    StationEventEntity(
                        tenantId = command.tenantId,
                        stationId = command.stationId,
                        type = "CERTIFICATE",
                        status = "${command.action}:$status".take(60),
                    )
                )
                db.audit(command.tenantId, "station:${command.stationId}", "STATION_${command.action}", "${command.stationId}/$status")
                if (command.action == "CertificateSigned" && status == "Accepted") {
                    val chain = db.decode(command.request).path("certificateChain").asText()
                    runCatching { certificates.fromHeader(chain).firstOrNull() }.getOrNull()?.let { leaf ->
                        station.issuedCertificateFingerprint = ChargerCertificates.fingerprint(leaf)
                        station.certificateSubject = leaf.subjectX500Principal.name.take(500)
                        station.certificateFingerprint = station.issuedCertificateFingerprint
                        station.certificateExpiresAt = leaf.notAfter.toInstant()
                        stations.save(station)
                    }
                }
            }
        }
    }

    @Scheduled(cron = "0 15 6 * * *")
    fun expiringCertificates() {
        val limit = Instant.now().plus(Duration.ofDays(30))
        stations.findAll().filter { !it.deleted && it.certificateExpiresAt?.isBefore(limit) == true }.forEach { station ->
            val expired = station.certificateExpiresAt!!.isBefore(Instant.now())
            runCatching {
                notifications.notifyAdmins(
                    station.key.tenantId,
                    NotificationType.STATION_CERTIFICATE_EXPIRING,
                    { msg(if (expired) "notification.certificate_expired.subject" else "notification.certificate_expiring.subject", station.name) },
                    {
                        msg(
                            if (expired) "notification.certificate_expired.body" else "notification.certificate_expiring.body",
                            station.name,
                            station.key.stationId,
                            station.certificateExpiresAt?.let {
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(I18n.locale()).withZone(ZoneId.of("Europe/Paris")).format(it)
                            },
                        )
                    },
                    dedupeKey = "certificate:${station.key.stationId}:${station.certificateFingerprint}",
                    window = Duration.ofDays(7),
                )
            }
        }
    }

    private fun redact(command: CommandEntity) {
        db.atomic {
            val locked = db.lock<CommandEntity>(command.id) ?: return@atomic
            if (locked.state in CommandState.ACTIVE) return@atomic
            val request = db.decode(locked.request)
            if (request.path("key").asText() != AUTHORIZATION_KEY) return@atomic
            locked.request = db.encode(mapOf("key" to AUTHORIZATION_KEY, "value" to REDACTED))
            commands.save(locked)
        }
    }

    private inline fun conflict(condition: Boolean, message: () -> String) {
        if (!condition) throw ResponseStatusException(HttpStatus.CONFLICT, message())
    }

    private fun locked(user: AppPrincipal, stationId: String): StationEntity =
        db.lock<StationEntity>(StationKey(user.tenantId, stationId))?.takeUnless { it.deleted }
            ?: throw NotFound(msg("stations.not_found"))

    private fun event(station: StationEntity, status: String, info: String? = null) {
        stationEvents.save(
            StationEventEntity(
                tenantId = station.key.tenantId,
                stationId = station.key.stationId,
                type = "SECURITY",
                status = status.take(60),
                info = info,
            )
        )
    }

    companion object {
        const val SECURITY_PROFILE_KEY = "SecurityProfile"
        const val AUTHORIZATION_KEY = "AuthorizationKey"
        const val REDACTED = "********"
        val CERTIFICATE_ACTIONS = setOf("CertificateSigned", "InstallCertificate", "DeleteCertificate")
        val ADMIN_CERTIFICATE_ACTIONS = setOf("InstallCertificate", "DeleteCertificate", "ExtendedTriggerMessage")

        fun protectedKey(key: String) =
            key.equals(SECURITY_PROFILE_KEY, ignoreCase = true) || key.equals(AUTHORIZATION_KEY, ignoreCase = true)
    }
}
