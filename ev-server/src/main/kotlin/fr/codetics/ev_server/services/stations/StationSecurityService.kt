package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.I18n
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.InvalidRequest
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
import fr.codetics.ev_server.models.stations.Iso15118Capabilities
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
import fr.codetics.ev_server.models.entities.StationParameterEntity
import fr.codetics.ev_server.models.entities.StationParameterKey
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.ocpp
import fr.codetics.ev_server.ocpp.v201.CertificateSummary
import fr.codetics.ev_server.ocpp.v201.ContractCertificateClient
import fr.codetics.ev_server.ocpp.v201.Component
import fr.codetics.ev_server.ocpp.v201.GetCertificateIdUseEnum
import fr.codetics.ev_server.ocpp.v201.GetInstalledCertificateIdsRequest
import fr.codetics.ev_server.ocpp.v201.InstallCertificateRequest
import fr.codetics.ev_server.ocpp.v201.InstallCertificateUseEnum
import fr.codetics.ev_server.ocpp.v201.Iso15118Certificates
import fr.codetics.ev_server.ocpp.v201.MessageTriggerEnum
import fr.codetics.ev_server.ocpp.v201.NetworkConnectionProfile
import fr.codetics.ev_server.ocpp.v201.OCPPInterfaceEnum
import fr.codetics.ev_server.ocpp.v201.OCPPTransportEnum
import fr.codetics.ev_server.ocpp.v201.OCPPVersionEnum
import fr.codetics.ev_server.ocpp.v201.Ocpp201Codec
import fr.codetics.ev_server.ocpp.v201.Ocpp201Variables
import fr.codetics.ev_server.ocpp.v201.ResetEnum
import fr.codetics.ev_server.ocpp.v201.ResetRequest
import fr.codetics.ev_server.ocpp.v201.SetNetworkProfileRequest
import fr.codetics.ev_server.ocpp.v201.SetVariableData
import fr.codetics.ev_server.ocpp.v201.SetVariablesRequest
import fr.codetics.ev_server.ocpp.v201.TriggerMessageRequest
import fr.codetics.ev_server.ocpp.v201.Variable
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationParameterRepository
import fr.codetics.ev_server.repositories.TenantRepository
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
    private val tenants: TenantRepository,
    private val parameters: StationParameterRepository,
    private val iso15118: Iso15118Certificates,
    private val contracts: ContractCertificateClient,
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
            iso15118 =
                Iso15118Capabilities(
                    enabled = iso15118.enabled,
                    certificatesDirectory = iso15118.directory.toString(),
                    v2gRoots = iso15118.v2gRoots.map(CertificateSummary::of),
                    cpoChain = iso15118.cpoChain.map(CertificateSummary::of),
                    moRoots = iso15118.moRoots.map(CertificateSummary::of),
                    contractService = contracts.configured,
                ),
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
                        if (station.ocpp() == OcppVersion.V201) networkProfile(station, target, input)
                        else
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

    private fun networkProfile(station: StationEntity, target: Int, input: SecurityProfileInput): UUID {
        val base = properties.ocpp.publicUrl.trim().trimEnd('/').ifBlank { null }
        conflict(base != null) { msg("security.public_url_required") }
        val tenant = station.key.tenantId
        val slug = tenants.findById(tenant).orElseThrow().slug
        val priority = priority(station)
        val slot = input.configurationSlot ?: (1..(priority.maxOrNull() ?: 1) + 1).first { it !in priority.ifEmpty { listOf(1) } }
        ensure(slot in 0..99) { msg("security.invalid_slot") }
        val network =
            input.ocppInterface?.let { name -> OCPPInterfaceEnum.entries.firstOrNull { it.name == name } ?: throw InvalidRequest(msg("security.invalid_interface")) }
                ?: OCPPInterfaceEnum.Wired0
        val timeout =
            parameters.findById(StationParameterKey(tenant, station.key.stationId, MESSAGE_TIMEOUT_KEY)).orElse(null)
                ?.value?.toIntOrNull()?.takeIf { it > 0 } ?: 30
        val request =
            SetNetworkProfileRequest(
                configurationSlot = slot,
                connectionData =
                    NetworkConnectionProfile(
                        ocppVersion = OCPPVersionEnum.OCPP20,
                        ocppTransport = OCPPTransportEnum.JSON,
                        ocppCsmsUrl = "${requireNotNull(base)}/$slug",
                        messageTimeout = timeout,
                        securityProfile = target,
                        ocppInterface = network,
                    ),
            )
        return queue.command(tenant, station.key.stationId, "SetNetworkProfile", Ocpp201Codec.encode(request), version = OcppVersion.V201)
    }

    private fun priority(station: StationEntity): List<Int> =
        parameters.findById(StationParameterKey(station.key.tenantId, station.key.stationId, PRIORITY_KEY)).orElse(null)
            ?.value.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }

    fun iso15118Command(user: AppPrincipal, stationId: String, action: String): List<UUID> {
        conflict(iso15118.enabled) { msg("iso15118.disabled") }
        val station = stationService.station(user.tenantId, stationId)
        conflict(station.ocpp() == OcppVersion.V201) { msg("iso15118.station_201") }
        val ids =
            when (action) {
                "install-roots" ->
                    (iso15118.v2gRoots.map { InstallCertificateUseEnum.V2GRootCertificate to it } +
                            iso15118.moRoots.map { InstallCertificateUseEnum.MORootCertificate to it })
                        .map { (type, certificate) ->
                            queue.command(
                                user.tenantId,
                                stationId,
                                "InstallCertificate",
                                Ocpp201Codec.encode(InstallCertificateRequest(type, ChargerCertificates.encode(certificate))),
                                version = OcppVersion.V201,
                            )
                        }
                "request-certificate" ->
                    listOf(
                        queue.command(
                            user.tenantId,
                            stationId,
                            "TriggerMessage",
                            Ocpp201Codec.encode(TriggerMessageRequest(requestedMessage = MessageTriggerEnum.SignV2GCertificate)),
                            version = OcppVersion.V201,
                        )
                    )
                "installed" ->
                    listOf(
                        queue.command(
                            user.tenantId,
                            stationId,
                            "GetInstalledCertificateIds",
                            Ocpp201Codec.encode(
                                GetInstalledCertificateIdsRequest(
                                    listOf(
                                        GetCertificateIdUseEnum.V2GRootCertificate,
                                        GetCertificateIdUseEnum.MORootCertificate,
                                        GetCertificateIdUseEnum.V2GCertificateChain,
                                    )
                                )
                            ),
                            version = OcppVersion.V201,
                        )
                    )
                else -> throw InvalidRequest(msg("iso15118.unknown_action"))
            }
        db.audit(user.tenantId, user.id.toString(), "STATION_ISO15118_${action.uppercase().replace('-', '_')}", stationId)
        return ids
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
            "SetNetworkProfile" -> if (command.ocppVersion == OcppVersion.V201.label) networkProfileCompleted(event)
            "SetVariables" -> if (command.ocppVersion == OcppVersion.V201.label) priorityCompleted(event)
            "Reset" -> if (command.ocppVersion == OcppVersion.V201.label) resetCompleted(event)
            "CertificateSigned", "InstallCertificate", "DeleteCertificate" -> {
                val station = stations.findById(StationKey(command.tenantId, command.stationId)).orElse(null) ?: return
                val status = if (event.success) event.result.path("status").asText() else command.state.name
                val v2g = db.decode(command.request).path("certificateType").asText() == "V2GCertificate"
                stationEvents.save(
                    StationEventEntity(
                        tenantId = command.tenantId,
                        stationId = command.stationId,
                        type = "CERTIFICATE",
                        status = "${command.action}:$status".take(60),
                    )
                )
                db.audit(command.tenantId, "station:${command.stationId}", "STATION_${command.action}", "${command.stationId}/$status")
                if (command.action == "CertificateSigned" && status == "Accepted" && v2g) {
                    val chain = db.decode(command.request).path("certificateChain").asText()
                    runCatching { iso15118.parse(chain).firstOrNull() }.getOrNull()?.let { leaf ->
                        station.v2gCertificateSubject = leaf.subjectX500Principal.name.take(500)
                        station.v2gCertificateExpiresAt = leaf.notAfter.toInstant()
                        stations.save(station)
                    }
                } else if (command.action == "CertificateSigned" && status == "Accepted") {
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

    private fun requested(event: CommandCompleted): StationEntity? {
        val station = stations.findById(StationKey(event.command.tenantId, event.command.stationId)).orElse(null) ?: return null
        return station.takeIf { it.requestedSecurityProfile != null }
    }

    private fun abandon(station: StationEntity, step: String, status: String?) {
        val requested = station.requestedSecurityProfile
        station.requestedSecurityProfile = null
        stations.save(station)
        event(station, "SecurityProfile${status ?: "Failed"}", "$step / $requested")
        db.audit(station.key.tenantId, "station:${station.key.stationId}", "STATION_SECURITY_PROFILE", "${station.key.stationId}/$requested/$step:${status ?: "Failed"}")
    }

    private fun networkProfileCompleted(event: CommandCompleted) {
        val station = requested(event) ?: return
        val request = db.decode(event.command.request)
        if (request.path("connectionData").path("securityProfile").intValue() != station.requestedSecurityProfile) return
        val status = if (event.success) event.result.path("status").asText() else null
        if (status != "Accepted") return abandon(station, "SetNetworkProfile", status)
        val slot = request.path("configurationSlot").intValue()
        val current = priority(station).ifEmpty { listOf(if (slot == 1) 2 else 1) }
        val order = (listOf(slot) + current.filter { it != slot }).joinToString(",")
        queue.command(
            station.key.tenantId,
            station.key.stationId,
            "SetVariables",
            Ocpp201Codec.encode(
                SetVariablesRequest(
                    listOf(
                        SetVariableData(
                            attributeValue = order,
                            component = Component(name = "OCPPCommCtrlr"),
                            variable = Variable("NetworkConfigurationPriority"),
                        )
                    )
                )
            ),
            version = OcppVersion.V201,
        )
        event(station, "SecurityProfileNetworkAccepted", "$slot → $order")
    }

    private fun priorityCompleted(event: CommandCompleted) {
        val request = db.decode(event.command.request).path("setVariableData")
        if (request.size() != 1 || request.path(0).path("variable").path("name").asText() != "NetworkConfigurationPriority") return
        val station = requested(event) ?: return
        val status = if (event.success) event.result.path("setVariableResult").path(0).path("attributeStatus").asText() else null
        if (status !in setOf("Accepted", "RebootRequired")) return abandon(station, "NetworkConfigurationPriority", status)
        parameters.save(
            (parameters.findById(StationParameterKey(station.key.tenantId, station.key.stationId, PRIORITY_KEY)).orElse(null)
                ?: StationParameterEntity(StationParameterKey(station.key.tenantId, station.key.stationId, PRIORITY_KEY)))
                .apply {
                    value = request.path(0).path("attributeValue").asText()
                    updatedAt = Instant.now()
                }
        )
        queue.command(
            station.key.tenantId,
            station.key.stationId,
            "Reset",
            Ocpp201Codec.encode(ResetRequest(ResetEnum.OnIdle)),
            version = OcppVersion.V201,
        )
        event(station, "SecurityProfilePriority$status")
    }

    private fun resetCompleted(event: CommandCompleted) {
        val station = requested(event) ?: return
        val status = if (event.success) event.result.path("status").asText() else null
        if (status !in setOf("Accepted", "Scheduled")) return abandon(station, "Reset", status)
        event(station, "SecurityProfileReset$status")
    }

    @Scheduled(cron = "0 15 6 * * *")
    fun expiringCertificates() {
        val limit = Instant.now().plus(Duration.ofDays(30))
        stations.findAll().filter { !it.deleted }.flatMap { station ->
            listOfNotNull(
                station.certificateExpiresAt?.takeIf { it.isBefore(limit) }?.let { Triple(station, it, station.certificateFingerprint) },
                station.v2gCertificateExpiresAt?.takeIf { it.isBefore(limit) }?.let { Triple(station, it, "v2g:${station.v2gCertificateSubject}") },
            )
        }.forEach { (station, expiresAt, fingerprint) ->
            val expired = expiresAt.isBefore(Instant.now())
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
                            expiresAt.let {
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(I18n.locale()).withZone(ZoneId.of("Europe/Paris")).format(it)
                            },
                        )
                    },
                    dedupeKey = "certificate:${station.key.stationId}:$fingerprint",
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
        const val PRIORITY_KEY = "OCPPCommCtrlr/NetworkConfigurationPriority"
        const val MESSAGE_TIMEOUT_KEY = "OCPPCommCtrlr/MessageTimeout:Default"
        val CERTIFICATE_ACTIONS = setOf("CertificateSigned", "InstallCertificate", "DeleteCertificate")
        val ADMIN_CERTIFICATE_ACTIONS = setOf("InstallCertificate", "DeleteCertificate", "ExtendedTriggerMessage")

        fun protectedKey(key: String) =
            key.equals(SECURITY_PROFILE_KEY, ignoreCase = true) ||
                key.equals(AUTHORIZATION_KEY, ignoreCase = true) ||
                Ocpp201Variables.protected(key)
    }
}
