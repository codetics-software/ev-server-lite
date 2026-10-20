package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ChargingTokenRepository : StoreRepository<ChargingTokenEntity, ChargingTokenKey> {
    fun findAllByKeyTenantId(tenant: UUID, pageable: Pageable): Page<ChargingTokenEntity>

    fun findAllByKeyTenantIdAndUserId(tenant: UUID, user: UUID): List<ChargingTokenEntity>
}
