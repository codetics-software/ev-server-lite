package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
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
) : TextWebSocketHandler(), SubProtocolCapable, SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<StationIdentity, WebSocketSession>()
    @Volatile private var running = false
    @Volatile private var lastPing = Instant.now()

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
        return listOf("ocpp1.6")
    }

    private fun identity(session: WebSocketSession): StationIdentity {
        return session.attributes["station"] as StationIdentity
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        session.setTextMessageSizeLimit(262144)
        val station = identity(session)
        val previous = sessions.put(station, session)
        previous?.close(CloseStatus.POLICY_VIOLATION)
        updateConnectivity(station, true, session.attributes["security"] as? ConnectionSecurity)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val station = identity(session)

        if (sessions.remove(station, session)) {
            updateConnectivity(station, false)
        }
    }

    private fun updateConnectivity(
        identity: StationIdentity,
        connected: Boolean,
        security: ConnectionSecurity? = null,
    ) {
        db.atomic {
            val station = db.lock<StationEntity>(StationKey(identity.tenant, identity.station))

            if (station != null) {
                station.connected = connected
                station.lastSeen = Instant.now()
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

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val station = identity(session)
        var id = ""

        try {
            val frame = db.json.readTree(message.payload)
            validateFrame(frame)
            id = frame[1].asText()

            when (frame[0].intValue()) {
                2 -> {
                    if (frame.size() != 4 || !frame[2].isString) {
                        throw OcppFault("FormationViolation", "Appel OCPP invalide.")
                    }
                    val response =
                        service.call(
                            station.tenant,
                            station.station,
                            id,
                            frame[2].asText(),
                            frame[3],
                            session.attributes["connection"].toString(),
                        )
                    send(session, response)
                }
                3,
                4 -> {
                    queue.complete(station, frame)
                }
                else -> {
                    throw OcppFault("ProtocolError", "Type de trame non pris en charge.")
                }
            }
        } catch (fault: OcppFault) {
            sendError(session, id, fault.code, fault.message)
        } catch (error: Exception) {
            log.warn(
                "Message OCPP non traité (borne {}/{}, message '{}')",
                station.tenant,
                station.station,
                id,
                error,
            )
            if (id.isNotBlank()) {
                sendError(session, id, "InternalError", "Le message n’a pas pu être traité.")
            } else {
                session.close(CloseStatus.BAD_DATA)
            }
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
            runCatching {
                synchronized(session) {
                    if (session.isOpen) session.sendMessage(PingMessage())
                }
            }
        }
    }

    private fun sendError(session: WebSocketSession, id: String, code: String, message: String) {
        send(session, db.encode(listOf(4, id, code, message, emptyMap<String, Any>())))
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
            val pending = queue.claim(station)

            pending.forEach { command ->
                try {
                    send(
                        session,
                        db.encode(
                            listOf(
                                2,
                                command.id.toString(),
                                command.action,
                                db.decode(command.request),
                            )
                        ),
                    )
                } catch (error: Exception) {
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
    }
}
