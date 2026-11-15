package fr.codetics.ev_server.services.commands

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.commands.CommandInput
import fr.codetics.ev_server.models.commands.CommandReceipt
import fr.codetics.ev_server.models.commands.CommandResponse
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.v201.Ocpp201Variables
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.services.stations.StationSecurityService
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
        val version =
            if (input.ocppVersion == null) OcppVersion.V16
            else OcppVersion.fromLabel(input.ocppVersion) ?: throw InvalidRequest(msg("ocpp.version.invalid"))
        if (version == OcppVersion.V201) native(user, input)
        ensure(input.action !in StationSecurityService.CERTIFICATE_ACTIONS) {
            msg("commands.certificates_in_security")
        }
        ensure(
            input.action != "ChangeConfiguration" ||
                !StationSecurityService.protectedKey(input.payload["key"]?.toString().orEmpty())
        ) {
            msg("commands.security_parameter")
        }
        ensure(
            user.role == UserRole.ADMIN ||
                input.action != "ExtendedTriggerMessage" ||
                input.payload["requestedMessage"] != "SignChargePointCertificate"
        ) {
            msg("commands.certificate_admin_only")
        }
        val id =
            queue.command(
                user.tenantId,
                stationId,
                input.action,
                db.json.valueToTree(input.payload),
                version = version,
            )
        db.audit(user.tenantId, user.id.toString(), "STATION_COMMAND_${input.action}", stationId)
        return CommandReceipt(id)
    }

    private fun native(user: AppPrincipal, input: CommandInput) {
        val variables = (input.payload["setVariableData"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        ensure(
            input.action != "SetNetworkProfile" &&
                variables.none { entry ->
                    val component = entry["component"] as? Map<*, *>
                    val variable = entry["variable"] as? Map<*, *>
                    Ocpp201Variables.protected("${component?.get("name")}/${variable?.get("name")}")
                }
        ) {
            msg("commands.security_parameter")
        }
        ensure(
            user.role == UserRole.ADMIN ||
                input.action != "TriggerMessage" ||
                input.payload["requestedMessage"]?.toString()?.startsWith("Sign") != true
        ) {
            msg("commands.certificate_admin_only")
        }
    }
}
