package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.persistence.CommandEntity
import fr.codetics.ev_server.persistence.CommandRepository
import fr.codetics.ev_server.persistence.StationKey
import fr.codetics.ev_server.persistence.StationRepository
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.*
import tools.jackson.databind.JsonNode

@Component
class OcppCommandQueue(
    private val db: Database,
    private val stations: StationRepository,
    private val commands: CommandRepository
) {
    fun complete(station: StationIdentity, frame: JsonNode) {
        val success = frame[0].intValue() == 3
        val valid = if (success) {
            frame.size() == 3 && frame[2].isObject
        } else {
            frame.size() == 5 && frame[2].isString && frame[3].isString && frame[4].isObject
        }

        if (!valid) {
            throw OcppFault("FormationViolation", "Invalid response")
        }

        val id = try {
            UUID.fromString(frame[1].asText())
        } catch (_: IllegalArgumentException) {
            return
        }

        db.atomic {
            val command = commands.findForUpdate(id, station.tenant, station.station)

            if (command != null && command.state == "SENT") {
                command.state = if (success) "SUCCEEDED" else "FAILED"
                command.result = db.encode(if (success) frame[2] else frame)
                command.completedAt = Instant.now()
                commands.save(command)
            }

            true
        }
    }

    fun command(tenant: UUID, station: String, action: String, payload: JsonNode): UUID {
        try {
            OcppValidation.validate(action, payload, true)
        } catch (fault: OcppFault) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, fault.message)
        }

        val device = stations.findById(StationKey(tenant, station)).orElse(null)

        if (device == null || !device.enabled) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        val command = commands.save(
            CommandEntity(
                tenantId = tenant,
                stationId = station,
                action = action,
                request = db.encode(payload)
            )
        )

        return command.id
    }

    fun expire() {
        db.atomic {
            val expired = commands.findAllByStateInAndDeadlineBefore(listOf("QUEUED", "SENT"), Instant.now())

            expired.forEach { command ->
                val locked = commands.findForUpdate(command.id, command.tenantId, command.stationId)

                if (locked != null && locked.state in setOf("QUEUED", "SENT")) {
                    locked.state = "TIMED_OUT"
                    locked.completedAt = Instant.now()
                    commands.save(locked)
                }
            }

            true
        }
    }

    fun claim(station: StationIdentity): List<CommandEntity> {
        return db.atomic {
            val pending = commands.findPending(
                station.tenant,
                station.station,
                Instant.now(),
                PageRequest.of(0, 10)
            )

            pending.forEach { command ->
                command.state = "SENT"
            }
            commands.saveAll(pending)

            // Commit before transmission. A transport failure must not replay a
            // non-idempotent command that the station may already have executed.
            pending
        }
    }

    fun fail(command: CommandEntity) {
        db.atomic {
            val locked = commands.findForUpdate(command.id, command.tenantId, command.stationId)

            if (locked != null && locked.state == "SENT") {
                locked.state = "FAILED"
                locked.completedAt = Instant.now()
                commands.save(locked)
            }

            true
        }
    }
}
