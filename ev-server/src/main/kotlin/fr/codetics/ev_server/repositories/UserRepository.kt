package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.UserEntity
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserRepository : StoreRepository<UserEntity, UUID> {
    fun findByTenantIdAndEmail(tenantId: UUID, email: String): UserEntity?

    fun findByTenantIdAndId(tenantId: UUID, id: UUID): UserEntity?

    fun findAllByTenantId(tenantId: UUID, pageable: Pageable): Page<UserEntity>
}
