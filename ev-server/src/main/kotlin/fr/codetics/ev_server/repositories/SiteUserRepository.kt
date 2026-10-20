package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.SiteUserEntity
import fr.codetics.ev_server.models.entities.SiteUserKey
import java.util.UUID

interface SiteUserRepository : StoreRepository<SiteUserEntity, SiteUserKey> {
    fun findAllByKeyTenantIdAndKeySiteId(tenant: UUID, site: UUID): List<SiteUserEntity>

    fun findAllByKeyTenantIdAndKeyUserId(tenant: UUID, user: UUID): List<SiteUserEntity>

    fun existsByKeyTenantIdAndKeySiteIdAndKeyUserId(tenant: UUID, site: UUID, user: UUID): Boolean

    fun findAllByKeyTenantId(tenant: UUID): List<SiteUserEntity>
}
