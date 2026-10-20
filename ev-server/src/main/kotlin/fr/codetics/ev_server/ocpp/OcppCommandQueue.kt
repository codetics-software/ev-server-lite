package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.commands.CommandState
import fr.codetics.ev_server.models.entities.CommandEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.StationRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class OcppCommandQueue(
    private val db: Database,
    private val stations: StationRepository,
    private val commands: CommandRepository,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
) {
    fun complete(station: StationIdentity, frame: JsonNode) {
        val success = frame[0].intValue() == 3
        val valid =
            if (success) {
                frame.size() == 3 && frame[2].isObject
            } else {
                frame.size() == 5 && frame[2].isString && frame[3].isString && frame[4].isObject
            }

        if (!valid) {
            throw OcppFault("FormationViolation", "Réponse invalide.")
        }

        val id =
            try {
                UUID.fromString(frame[1].asText())
            } catch (_: IllegalArgumentException) {
                return
            }

        db.atomic {
            val command =
                db.lock<CommandEntity>(id)?.takeIf {
                    it.tenantId == station.tenant && it.stationId == station.station
                }

            if (command != null && command.state == CommandState.SENT) {
                command.state = if (success) CommandState.SUCCEEDED else CommandState.FAILED
                command.result = db.encode(if (success) frame[2] else frame)
                command.completedAt = Instant.now(clock)
                commands.save(command)
                events.publishEvent(
                    CommandCompleted(command, success, if (success) frame[2] else frame[4])
                )
            }
        }
    }

    fun command(
        tenant: UUID,
        station: String,
        action: String,
        payload: JsonNode,
        callback: String? = null,
    ): UUID {
        try {
            OcppValidation.validate(action, payload, true)
        } catch (fault: OcppFault) {
            throw InvalidRequest(fault.message)
        }

        val device = stations.findById(StationKey(tenant, station)).orElse(null)

        if (device == null || !device.enabled || device.deleted) {
            throw NotFound("Borne introuvable ou désactivée.")
        }

        val command =
            commands.save(
                CommandEntity(
                    tenantId = tenant,
                    stationId = station,
                    action = action,
                    request = db.encode(payload),
                    callback = callback,
                )
            )

        return command.id
    }

    fun expire() {
        db.atomic {
            val expired =
                commands.findAllByStateInAndDeadlineBefore(CommandState.ACTIVE, Instant.now(clock))

            expired.forEach { command ->
                val locked = db.lock<CommandEntity>(command.id)

                if (locked != null && locked.state in CommandState.ACTIVE) {
                    locked.state = CommandState.TIMED_OUT
                    locked.completedAt = Instant.now(clock)
                    commands.save(locked)
                    events.publishEvent(CommandCompleted(locked, false, db.json.nullNode()))
                }
            }
        }
    }

    fun claim(station: StationIdentity): List<CommandEntity> {
        return db.atomic {
            val now = Instant.now(clock)
            val pending =
                commands
                    .findAllByTenantIdAndStationIdAndStateAndDeadlineAfterOrderByCreatedAt(
                        station.tenant,
                        station.station,
                        CommandState.QUEUED,
                        now,
                        PageRequest.of(0, 10),
                    )
                    .mapNotNull { db.lock<CommandEntity>(it.id) }
                    .filter { it.state == CommandState.QUEUED && it.deadline.isAfter(now) }

            pending.forEach { command -> command.state = CommandState.SENT }
            commands.saveAll(pending)

            // Valider avant l’envoi : une erreur de transport ne doit pas rejouer
            // une commande que la borne a peut-être déjà exécutée.
            pending
        }
    }

    fun fail(command: CommandEntity) {
        db.atomic {
            val locked = db.lock<CommandEntity>(command.id)

            if (locked != null && locked.state == CommandState.SENT) {
                locked.state = CommandState.FAILED
                locked.completedAt = Instant.now(clock)
                commands.save(locked)
                events.publishEvent(CommandCompleted(locked, false, db.json.nullNode()))
            }
        }
    }
}
