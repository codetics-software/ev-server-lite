package fr.codetics.ev_server.services.commands

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.commands.CommandInput
import fr.codetics.ev_server.models.commands.CommandReceipt
import fr.codetics.ev_server.models.commands.CommandResponse
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.repositories.CommandRepository
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class CommandService(
    private val commands: CommandRepository,
    private val queue: OcppCommandQueue,
    private val db: Database,
) {
    fun list(tenantId: UUID): List<CommandResponse> =
        commands.findAllByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 100)).map {
            command ->
            CommandResponse(
                command.id,
                command.stationId,
                command.action,
                command.state,
                command.result?.let(db::decode),
                command.createdAt,
                command.completedAt,
            )
        }

    fun queue(user: AppPrincipal, stationId: String, input: CommandInput): CommandReceipt {
        val id =
            queue.command(
                user.tenantId,
                stationId,
                input.action,
                db.json.valueToTree(input.payload),
            )
        db.audit(user.tenantId, user.id.toString(), "STATION_COMMAND_${input.action}", stationId)
        return CommandReceipt(id)
    }
}
