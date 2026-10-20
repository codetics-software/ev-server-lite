package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.RegistrationTokenEntity
import java.util.UUID

interface RegistrationTokenRepository : StoreRepository<RegistrationTokenEntity, UUID> {
    fun findAllByTenantIdOrderByCreatedAtDesc(tenant: UUID): List<RegistrationTokenEntity>

    fun findByTenantIdAndId(tenant: UUID, id: UUID): RegistrationTokenEntity?

    fun findByTenantIdAndTokenHash(tenant: UUID, hash: String): RegistrationTokenEntity?
}
