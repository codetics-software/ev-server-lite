package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.commands.CommandState
import fr.codetics.ev_server.models.entities.CommandEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.ocpp.v201.Ocpp201Commands
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
    private val translator: Ocpp201Commands,
) {
    /** Enregistre la réponse de la borne ; renvoie l’action de la commande concernée, si elle existe. */
    fun complete(station: StationIdentity, frame: JsonNode): String? {
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
                return null
            }

        return db.atomic {
            val command =
                db.lock<CommandEntity>(id)?.takeIf {
                    it.tenantId == station.tenant && it.stationId == station.station
                }

            if (command != null && command.state == CommandState.SENT) {
                val result = if (success) translator.inbound(command, frame[2]) else frame[4]
                command.state = if (success) CommandState.SUCCEEDED else CommandState.FAILED
                command.result = db.encode(if (success) result else frame)
                command.completedAt = Instant.now(clock)
                commands.save(command)
                events.publishEvent(CommandCompleted(command, success, result))
            }
            command?.action
        }
    }

    fun command(
        tenant: UUID,
        station: String,
        action: String,
        payload: JsonNode,
        callback: String? = null,
        version: OcppVersion = OcppVersion.V16,
    ): UUID {
        try {
            if (version == OcppVersion.V16) OcppValidation.validate(action, payload, true)
            else translator.native(action, payload)
        } catch (fault: OcppFault) {
            throw InvalidRequest(fault.message)
        }

        val device = stations.findById(StationKey(tenant, station)).orElse(null)

        if (device == null || !device.enabled || device.deleted) {
            throw NotFound(msg("stations.not_found_or_disabled"))
        }
        val id = UUID.randomUUID()
        if (version == OcppVersion.V201 && device.ocpp() == OcppVersion.V16) {
            throw InvalidRequest(msg("ocpp.version.station_16"))
        }
        if (version == OcppVersion.V16 && device.ocpp() == OcppVersion.V201) {
            try {
                translator.check(id, tenant, station, action, payload)
            } catch (fault: OcppFault) {
                throw InvalidRequest(fault.message)
            }
        }

        val command =
            commands.save(
                CommandEntity(
                    id = id,
                    tenantId = tenant,
                    stationId = station,
                    action = action,
                    request = db.encode(payload),
                    callback = callback,
                    ocppVersion = version.label.takeIf { version != OcppVersion.V16 },
                )
            )

        return command.id
    }

    fun supports(device: StationEntity, action: String, payload: JsonNode): Boolean {
        if (device.ocpp() != OcppVersion.V201) return true
        return runCatching {
            translator.check(UUID.randomUUID(), device.key.tenantId, device.key.stationId, action, payload)
        }.isSuccess
    }

    fun interrupted(station: StationIdentity) {
        db.atomic {
            commands
                .findAllByTenantIdAndStationIdAndStateAndDeadlineAfterOrderByCreatedAt(
                    station.tenant,
                    station.station,
                    CommandState.SENT,
                    Instant.EPOCH,
                    PageRequest.of(0, 50),
                )
                .mapNotNull { db.lock<CommandEntity>(it.id) }
                .filter { it.state == CommandState.SENT }
                .forEach { command ->
                    command.state = CommandState.TIMED_OUT
                    command.completedAt = Instant.now(clock)
                    commands.save(command)
                    events.publishEvent(CommandCompleted(command, false, db.json.nullNode()))
                }
        }
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

    fun claim(station: StationIdentity, version: OcppVersion = OcppVersion.V16): List<CommandEntity> {
        return db.atomic {
            val now = Instant.now(clock)
            val waiting =
                commands
                    .findAllByTenantIdAndStationIdAndStateAndDeadlineAfterOrderByCreatedAt(
                        station.tenant,
                        station.station,
                        CommandState.SENT,
                        now,
                        PageRequest.of(0, 1),
                    )
                    .isNotEmpty()
            if (waiting) return@atomic emptyList()
            val registration = stations.findById(StationKey(station.tenant, station.station)).orElse(null)?.registrationStatus
            if (registration == StationRegistration.REJECTED) return@atomic emptyList()
            val restricted = registration == StationRegistration.PENDING
            val pending =
                commands
                    .findAllByTenantIdAndStationIdAndStateAndDeadlineAfterOrderByCreatedAt(
                        station.tenant,
                        station.station,
                        CommandState.QUEUED,
                        now,
                        PageRequest.of(0, if (restricted) 50 else 1),
                    )
                    .filter { !restricted || StationRegistration.allowsCommand(registration, it.action) }
                    .take(1)
                    .mapNotNull { db.lock<CommandEntity>(it.id) }
                    .filter { it.state == CommandState.QUEUED && it.deadline.isAfter(now) }

            pending.forEach { command ->
                command.state = CommandState.SENT
                command.wireVersion = version.label
                command.deadline = maxOf(command.deadline, now.plusSeconds(RESPONSE_SECONDS))
            }
            commands.saveAll(pending)

            // Valider avant l’envoi : une erreur de transport ne doit pas rejouer
            // une commande que la borne a peut-être déjà exécutée.
            pending
        }
    }

    fun fail(command: CommandEntity, reason: String? = null) {
        db.atomic {
            val locked = db.lock<CommandEntity>(command.id)

            if (locked != null && locked.state == CommandState.SENT) {
                val result = reason?.let { db.json.valueToTree<JsonNode>(mapOf("error" to it)) }
                locked.state = CommandState.FAILED
                locked.completedAt = Instant.now(clock)
                locked.result = result?.let(db::encode)
                commands.save(locked)
                events.publishEvent(CommandCompleted(locked, false, result ?: db.json.nullNode()))
            }
        }
    }

    companion object {
        const val RESPONSE_SECONDS = 60L
    }
}
