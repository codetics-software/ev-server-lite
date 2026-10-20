package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationParameterEntity
import fr.codetics.ev_server.models.entities.StationParameterKey
import fr.codetics.ev_server.models.stations.TemplateApplication
import fr.codetics.ev_server.ocpp.CommandCompleted
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.StationBooted
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.StationParameterRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.services.organization.Organization
import java.time.Instant
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class StationConfigurationService(
    private val db: Database,
    private val organization: Organization,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val parameters: StationParameterRepository,
    private val queue: OcppCommandQueue,
) {
    fun apply(station: StationEntity): TemplateApplication {
        val tenant = station.key.tenantId
        val template =
            organization.templates(tenant).firstOrNull {
                it.data.matches(station.vendor, station.model, station.firmwareVersion)
            } ?: return TemplateApplication(null, 0)

        station.templateId = template.id
        if (station.maximumPowerKw == null) station.maximumPowerKw = template.data.maximumPowerKw
        stations.save(station)

        template.data.connectors.forEach { definition ->
            val key = ConnectorKey(tenant, station.key.stationId, definition.connectorId)
            val connector = connectors.findById(key).orElse(null) ?: ConnectorEntity(key = key)
            connector.type = connector.type ?: definition.type
            connector.maximumPowerKw = connector.maximumPowerKw ?: definition.maximumPowerKw
            connector.phases = connector.phases ?: definition.phases
            connectors.save(connector)
        }

        val current =
            parameters.findAllByKeyTenantIdAndKeyStationIdOrderByKeyKey(tenant, station.key.stationId)
                .associateBy { it.key.key }
        var queued = 0
        template.data.parameters.forEach { parameter ->
            val existing = current[parameter.key]
            if (existing?.readonly == true || existing?.value == parameter.value) return@forEach
            queue.command(
                tenant,
                station.key.stationId,
                "ChangeConfiguration",
                db.json.valueToTree(mapOf("key" to parameter.key, "value" to parameter.value)),
            )
            queued += 1
        }
        return TemplateApplication(template.id, queued)
    }

    @EventListener
    fun booted(event: StationBooted) {
        val station = event.station
        if (event.firstBoot) {
            queue.command(
                station.key.tenantId,
                station.key.stationId,
                "GetConfiguration",
                db.json.createObjectNode(),
            )
        }
        apply(station)
    }

    @EventListener
    fun completed(event: CommandCompleted) {
        val command = event.command
        if (!event.success) return
        when (command.action) {
            "GetConfiguration" -> {
                val now = Instant.now()
                event.result.path("configurationKey").forEach { entry ->
                    val key = entry.path("key").asText()
                    if (key.isBlank() || key.length > 100) return@forEach
                    parameters.save(
                        StationParameterEntity(
                            StationParameterKey(command.tenantId, command.stationId, key),
                            entry.get("value")?.asText()?.take(5000),
                            entry.path("readonly").asBoolean(false),
                            now,
                        )
                    )
                }
            }
            "ChangeConfiguration" -> {
                val status = event.result.path("status").asText()
                if (status !in setOf("Accepted", "RebootRequired")) return
                val request = db.decode(command.request)
                val key = StationParameterKey(command.tenantId, command.stationId, request.path("key").asText())
                val parameter = parameters.findById(key).orElse(null) ?: StationParameterEntity(key)
                parameter.value = request.path("value").asText()
                parameter.updatedAt = Instant.now()
                parameters.save(parameter)
            }
        }
    }
}
