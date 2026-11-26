package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.ocpp.v201.Ocpp201Commands
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.JsonNode

@Component
class OcppGateway(
    private val db: Database,
    private val service: OcppService,
    private val stations: StationRepository,
    private val queue: OcppCommandQueue,
    private val stationEvents: StationEventRepository,
    private val events: ApplicationEventPublisher,
    private val translator: Ocpp201Commands,
    private val telemetry: OcppTelemetry,
) : TextWebSocketHandler(), SubProtocolCapable, SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<StationIdentity, WebSocketSession>()
    private val activity = ConcurrentHashMap<String, Instant>()
    private val ponged = ConcurrentHashMap.newKeySet<String>()
    private val handling = ConcurrentHashMap.newKeySet<StationIdentity>()
    @Volatile private var running = false
    @Volatile private var lastPing = Instant.now()

    init {
        OcppVersion.entries.forEach { version ->
            telemetry.connections(version) { sessions.values.count { version(it) == version } }
        }
    }

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
        sessions.values.forEach { session ->
            runCatching { session.close(CloseStatus.SERVICE_RESTARTED) }
        }
    }

    override fun isRunning() = running

    override fun getSubProtocols(): List<String> {
        return listOf(OcppVersion.V201.subprotocol, OcppVersion.V16.subprotocol)
    }

    private fun identity(session: WebSocketSession): StationIdentity? = session.attributes["station"] as? StationIdentity

    private fun version(session: WebSocketSession): OcppVersion =
        session.attributes["ocpp"] as? OcppVersion ?: OcppVersion.V16

    override fun afterConnectionEstablished(session: WebSocketSession) {
        if (session.attributes.containsKey(UNSUPPORTED_PROTOCOL)) {
            session.close(CloseStatus.PROTOCOL_ERROR)
            return
        }
        session.setTextMessageSizeLimit(262144)
        activity[session.id] = Instant.now()
        val station =
            identity(session) ?: run {
                session.close(CloseStatus.POLICY_VIOLATION)
                return
            }
        val previous = sessions.put(station, session)
        previous?.close(CloseStatus.POLICY_VIOLATION)
        queue.interrupted(station)
        updateConnectivity(station, true, session.attributes["security"] as? ConnectionSecurity, version(session))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        activity.remove(session.id)
        ponged.remove(session.id)
        val station = identity(session) ?: return
        telemetry.disconnected(version(session), status.code)

        if (sessions.remove(station, session)) {
            updateConnectivity(station, false)
        }
    }

    private fun updateConnectivity(
        identity: StationIdentity,
        connected: Boolean,
        security: ConnectionSecurity? = null,
        version: OcppVersion = OcppVersion.V16,
    ) {
        db.atomic {
            val station = db.lock<StationEntity>(StationKey(identity.tenant, identity.station))

            if (station != null) {
                station.connected = connected
                station.lastSeen = Instant.now()
                if (connected) station.ocppVersion = version.label
                if (connected && security != null) {
                    station.connectionSecurityProfile = security.profile
                    security.certificate?.let { certificate ->
                        station.certificateSubject = certificate.subject
                        station.certificateFingerprint = certificate.fingerprint
                        station.certificateExpiresAt = certificate.expiresAt
                    }
                    if (security.promoteSecret && station.pendingSecretHash != null) {
                        station.secretHash = requireNotNull(station.pendingSecretHash)
                        station.pendingSecretHash = null
                        securityEvent(identity, "PasswordRotated")
                        db.audit(identity.tenant, "station:${identity.station}", "STATION_SECRET_ROTATE_COMPLETED", identity.station)
                    }
                    station.requestedSecurityProfile?.takeIf { security.profile >= it }?.let { reached ->
                        station.securityProfile = reached
                        station.requestedSecurityProfile = null
                        securityEvent(identity, "SecurityProfileApplied", reached.toString())
                        db.audit(identity.tenant, "station:${identity.station}", "STATION_SECURITY_PROFILE", "${identity.station}/$reached/completed")
                    }
                }
                stations.save(station)
                stationEvents.save(
                    StationEventEntity(
                        tenantId = identity.tenant,
                        stationId = identity.station,
                        type = if (connected) "CONNECTION" else "DISCONNECTION",
                        status = if (connected) security?.let { "Profil ${it.profile}" } else null,
                        info = if (connected) "OCPP ${version.label}" else null,
                    )
                )
                events.publishEvent(StationConnectionChanged(station, connected))
            }
        }
    }

    private fun securityEvent(identity: StationIdentity, status: String, info: String? = null) {
        stationEvents.save(
            StationEventEntity(
                tenantId = identity.tenant,
                stationId = identity.station,
                type = "SECURITY",
                status = status,
                info = info,
            )
        )
    }

    fun disconnect(identity: StationIdentity) {
        sessions[identity]?.close(CloseStatus.POLICY_VIOLATION)
    }

    fun isConnected(identity: StationIdentity) = sessions[identity]?.isOpen == true

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        activity[session.id] = Instant.now()
        ponged.add(session.id)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        if (session.attributes.containsKey(UNSUPPORTED_PROTOCOL)) return
        val station = identity(session) ?: return
        val version = version(session)
        activity[session.id] = Instant.now()
        var id = ""
        var response = false
        var kind = "invalid"
        var action: String? = null
        var observation: io.micrometer.observation.Observation? = null

        try {
            val frame = db.json.readTree(message.payload)
            response = frame.isArray && frame.size() > 0 && frame[0].isIntegralNumber && frame[0].intValue() in 3..4
            // L’identifiant est lu avant la validation : une trame mal formée reçoit une CallError
            // portant son identifiant, que la borne peut rapprocher de sa demande.
            if (frame.isArray && frame.size() > 1 && frame[1].isString) {
                id = frame[1].asText().takeIf { it.isNotBlank() && it.length <= 36 }.orEmpty()
            }
            validateFrame(frame)

            when (frame[0].intValue()) {
                2 -> {
                    kind = "call"
                    if (frame.size() != 4 || !frame[2].isString) {
                        throw OcppFault("FormationViolation", "Appel OCPP invalide.")
                    }
                    action = frame[2].asText()
                    handling.add(station)
                    observation = telemetry.call(station, action, id, version)
                    try {
                        val answer =
                            observation.openScope().use {
                                service.call(
                                    station.tenant,
                                    station.station,
                                    id,
                                    action,
                                    frame[3],
                                    session.attributes["connection"].toString(),
                                    version,
                                )
                            }
                        telemetry.outcome(observation, "ok")
                        observation = null
                        send(session, answer)
                        telemetry.frame("outbound", "result", action, version, answer.length)
                    } finally {
                        handling.remove(station)
                    }
                }
                3,
                4 -> {
                    kind = if (frame[0].intValue() == 3) "result" else "error"
                    val completed = queue.complete(station, frame)
                    telemetry.command(completed, version, if (kind == "result") "accepted" else "error")
                }
                else -> {
                    throw OcppFault(
                        if (version == OcppVersion.V201) "MessageTypeNotSupported" else "ProtocolError",
                        "Type de trame non pris en charge.",
                    )
                }
            }
        } catch (fault: OcppFault) {
            observation?.let { telemetry.outcome(it, fault.code, fault) }
            if (response) {
                log.warn("Réponse OCPP ignorée (borne {}/{}, message '{}') : {}", station.tenant, station.station, id, fault.message)
            } else if (version == OcppVersion.V201) {
                sendError(
                    session,
                    id.ifBlank { "-1" },
                    if (fault.code == "FormationViolation") "RpcFrameworkError" else fault.code,
                    fault.message.take(255),
                )
            } else {
                sendError(session, id, fault.code, fault.message)
            }
        } catch (error: Exception) {
            observation?.let { telemetry.outcome(it, "InternalError", error) }
            if (response) {
                log.warn("Réponse OCPP non traitée (borne {}/{}, message '{}')", station.tenant, station.station, id, error)
                return
            }
            log.warn(
                "Message OCPP non traité (borne {}/{}, message '{}')",
                station.tenant,
                station.station,
                id,
                error,
            )
            // Une trame illisible n’entraîne jamais la fermeture de la connexion : la borne
            // se reconnecterait en boucle. OCPP 1.6 n’a pas de CallError sans identifiant.
            if (id.isNotBlank()) {
                sendError(session, id, "InternalError", "Le message n’a pas pu être traité.")
            } else if (version == OcppVersion.V201) {
                sendError(session, "-1", "RpcFrameworkError", "Le message n’a pas pu être lu.")
            }
        } finally {
            telemetry.frame("inbound", kind, action, version, message.payloadLength)
        }
    }

    private fun validateFrame(frame: JsonNode) {
        if (
            !frame.isArray ||
                frame.size() !in 3..5 ||
                !frame[0].isIntegralNumber ||
                !frame[1].isString
        ) {
            throw OcppFault("FormationViolation", "Trame OCPP invalide.")
        }
        if (frame[1].asText().isBlank() || frame[1].asText().length > 36) {
            throw OcppFault("FormationViolation", "Identifiant de message invalide.")
        }
    }

    private fun ping() {
        val now = Instant.now()
        if (now.isBefore(lastPing.plusSeconds(PING_SECONDS))) return
        lastPing = now
        sessions.values.forEach { session ->
            val seen = activity[session.id] ?: now
            val limit = if (session.id in ponged) PING_SECONDS * MISSED_PINGS else SILENT_SECONDS
            if (seen.isBefore(now.minusSeconds(limit))) {
                log.warn("Connexion OCPP sans réponse fermée (borne {})", identity(session)?.station)
                runCatching { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
                return@forEach
            }
            runCatching {
                synchronized(session) {
                    if (session.isOpen) session.sendMessage(PingMessage())
                }
            }
        }
    }

    private fun sendError(session: WebSocketSession, id: String, code: String, message: String) {
        val frame = db.encode(listOf(4, id, code, message, emptyMap<String, Any>()))
        try {
            send(session, frame)
            telemetry.frame("outbound", "error", null, version(session), frame.length)
        } catch (error: Exception) {
            log.debug("CallError OCPP non envoyée", error)
        }
    }

    private fun send(session: WebSocketSession, frame: String) {
        synchronized(session) {
            if (session.isOpen) {
                session.sendMessage(TextMessage(frame))
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    fun dispatch() {
        queue.expire()
        ping()

        sessions.forEach { (station, session) ->
            if (station in handling) return@forEach
            val version = version(session)
            val pending = queue.claim(station, version)

            pending.forEach { command ->
                val wire =
                    try {
                        translator.outbound(command, version)
                    } catch (fault: OcppFault) {
                        queue.fail(command, fault.message)
                        return@forEach
                    }
                try {
                    val frame = db.encode(listOf(2, command.id.toString(), wire.action, wire.payload))
                    send(session, frame)
                    telemetry.frame("outbound", "call", wire.action, version, frame.length)
                    telemetry.command(wire.action, version, "sent")
                } catch (error: Exception) {
                    telemetry.command(wire.action, version, "failed")
                    log.warn(
                        "Envoi de la commande {} à la borne {} impossible",
                        command.id,
                        station.station,
                        error,
                    )
                    queue.fail(command)
                }
            }
        }
    }

    companion object {
        const val PING_SECONDS = 30L
        const val MISSED_PINGS = 3L
        const val SILENT_SECONDS = 900L
    }
}
