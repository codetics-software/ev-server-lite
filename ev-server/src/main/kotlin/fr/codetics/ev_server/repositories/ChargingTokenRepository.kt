package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ChargingTokenRepository : JpaRepository<ChargingTokenEntity, ChargingTokenKey> {
    fun findAllByKeyTenantId(tenant: UUID, pageable: Pageable): Page<ChargingTokenEntity>
}
