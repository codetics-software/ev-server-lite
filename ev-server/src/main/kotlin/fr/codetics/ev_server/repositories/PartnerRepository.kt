package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.PartnerEntity
import java.util.UUID

interface PartnerRepository : StoreRepository<PartnerEntity, UUID> {
    fun findAllByTenantIdOrderByName(tenant: UUID): List<PartnerEntity>

    fun findByTokenHashAndLocalRoleAndEnabledTrue(hash: String, role: String): PartnerEntity?

    fun findByTenantIdAndId(tenant: UUID, id: UUID): PartnerEntity?
}
