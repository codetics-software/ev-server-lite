package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.persistence.StationKey
import fr.codetics.ev_server.persistence.StationRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
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
    private val queue: OcppCommandQueue
) : TextWebSocketHandler(), SubProtocolCapable {
    private val sessions = ConcurrentHashMap<StationIdentity, WebSocketSession>()

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
        updateConnectivity(station, true)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val station = identity(session)

        if (sessions.remove(station, session)) {
            updateConnectivity(station, false)
        }
    }

    private fun updateConnectivity(identity: StationIdentity, connected: Boolean) {
        db.atomic {
            val station = stations.findForUpdate(StationKey(identity.tenant, identity.station))

            if (station != null) {
                station.connected = connected
                station.lastSeen = Instant.now()
                stations.save(station)
            }

            true
        }
    }

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
                        throw OcppFault("FormationViolation", "Invalid CALL")
                    }
                    val response = service.call(station.tenant, station.station, id, frame[2].asText(), frame[3])
                    send(session, response)
                }
                3, 4 -> {
                    queue.complete(station, frame)
                }
                else -> {
                    throw OcppFault("ProtocolError", "Unsupported frame type")
                }
            }
        } catch (fault: OcppFault) {
            sendError(session, id, fault.code, fault.message)
        } catch (_: Exception) {
            if (id.isNotBlank()) {
                sendError(session, id, "InternalError", "Message could not be processed")
            } else {
                session.close(CloseStatus.BAD_DATA)
            }
        }
    }

    private fun validateFrame(frame: JsonNode) {
        if (!frame.isArray || frame.size() !in 3..5 || !frame[0].isIntegralNumber || !frame[1].isString) {
            throw OcppFault("FormationViolation", "Invalid OCPP frame")
        }
        if (frame[1].asText().isBlank() || frame[1].asText().length > 36) {
            throw OcppFault("FormationViolation", "Invalid message ID")
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

        sessions.forEach { (station, session) ->
            val pending = queue.claim(station)

            pending.forEach { command ->
                try {
                    send(session, db.encode(listOf(2, command.id.toString(), command.action, db.decode(command.request))))
                } catch (_: Exception) {
                    queue.fail(command)
                }
            }
        }
    }

}
