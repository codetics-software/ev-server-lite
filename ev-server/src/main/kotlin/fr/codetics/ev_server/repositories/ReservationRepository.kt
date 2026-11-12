package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.ReservationEntity
import fr.codetics.ev_server.models.entities.ReservationKey
import java.util.UUID
import org.springframework.data.domain.Pageable

interface ReservationRepository : StoreRepository<ReservationEntity, ReservationKey> {
    fun findAllByKeyTenantIdOrderByCreatedAtDesc(tenant: UUID, pageable: Pageable): List<ReservationEntity>

    fun findAllByKeyTenantIdAndKeyStationIdOrderByCreatedAtDesc(
        tenant: UUID,
        station: String,
        pageable: Pageable,
    ): List<ReservationEntity>
}
