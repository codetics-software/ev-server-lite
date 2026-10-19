package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.stations.ProvisionedStation
import fr.codetics.ev_server.models.stations.StationInput
import fr.codetics.ev_server.models.stations.StationResponse
import fr.codetics.ev_server.ocpp.STATION_ID
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.StationRepository
import java.util.UUID
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class StationService(
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val passwords: PasswordEncoder,
    private val db: Database,
) {
    fun list(tenantId: UUID, offset: Int, limit: Int): PageResponse<StationResponse> {
        val page =
            stations.findAllByKeyTenantId(
                tenantId,
                pageRequest(offset, limit, Sort.by("name", "key.stationId")),
            )
        return PageResponse(page.content.map { StationResponse.from(it) }, page.totalElements)
    }

    fun find(tenantId: UUID, stationId: String): StationResponse {
        val station =
            stations.findById(StationKey(tenantId, stationId)).orElse(null)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Borne introuvable.")
        val stationConnectors =
            connectors.findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(tenantId, stationId)
        return StationResponse.from(station, stationConnectors)
    }

    fun provision(user: AppPrincipal, input: StationInput): ProvisionedStation {
        ensure(input.id.matches(STATION_ID)) {
            "Identifiant de borne invalide."
        }
        ensure(input.name.isNotBlank() && input.name.length <= 200) {
            "Le nom de la borne doit contenir entre 1 et 200 caractères."
        }

        return db.atomic {
            val key = StationKey(user.tenantId, input.id)
            if (stations.existsById(key)) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cet identifiant de borne existe déjà.",
                )
            }
            val secret = randomToken()
            stations.save(
                StationEntity(key, input.name, requireNotNull(passwords.encode(secret)))
            ).also { db.flush() }
            db.audit(user.tenantId, user.id.toString(), "STATION_CREATE", input.id)
            ProvisionedStation(input.id, secret)
        }
    }
}
