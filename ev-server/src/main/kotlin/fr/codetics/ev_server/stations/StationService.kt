package fr.codetics.ev_server.stations

import fr.codetics.ev_server.api.PageResponse
import fr.codetics.ev_server.api.pageRequest
import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.persistence.ConnectorRepository
import fr.codetics.ev_server.persistence.StationEntity
import fr.codetics.ev_server.persistence.StationKey
import fr.codetics.ev_server.persistence.StationRepository
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
    private val db: Database
) {
    fun list(tenantId: UUID, offset: Int, limit: Int): PageResponse<StationResponse> {
        val page = stations.findAllByKeyTenantId(
            tenantId, pageRequest(offset, limit, Sort.by("name", "key.stationId"))
        )
        return PageResponse(page.content.map { StationResponse.from(it) }, page.totalElements)
    }

    fun find(tenantId: UUID, stationId: String): StationResponse {
        val station = stations.findById(StationKey(tenantId, stationId)).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found")
        val stationConnectors = connectors.findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(tenantId, stationId)
        return StationResponse.from(station, stationConnectors)
    }

    fun provision(user: AppPrincipal, input: StationInput): ProvisionedStation {
        require(input.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid station identity" }
        require(input.name.isNotBlank() && input.name.length <= 200) { "Station name must contain 1 to 200 characters" }

        return db.atomic {
            val key = StationKey(user.tenantId, input.id)
            if (stations.existsById(key)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Station identity already exists")
            }
            val secret = randomToken()
            stations.saveAndFlush(StationEntity(key, input.name, requireNotNull(passwords.encode(secret))))
            db.audit(user.tenantId, user.id.toString(), "STATION_CREATE", input.id)
            ProvisionedStation(input.id, secret)
        }
    }
}
