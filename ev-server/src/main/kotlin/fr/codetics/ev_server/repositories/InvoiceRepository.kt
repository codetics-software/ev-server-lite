package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.InvoiceEntity
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface InvoiceRepository : StoreRepository<InvoiceEntity, UUID> {
    fun findByTenantIdAndId(tenant: UUID, id: UUID): InvoiceEntity?

    fun findAllByTenantId(tenant: UUID, pageable: Pageable): Page<InvoiceEntity>

    fun findAllByTenantIdAndUserId(tenant: UUID, user: UUID, pageable: Pageable): Page<InvoiceEntity>

    fun findAllByTenantIdAndStatus(tenant: UUID, status: String, pageable: Pageable): Page<InvoiceEntity>

    fun findAllByTenantIdAndUserIdAndStatus(
        tenant: UUID,
        user: UUID,
        status: String,
        pageable: Pageable,
    ): Page<InvoiceEntity>

    fun findByTenantIdAndUserIdAndPeriodKeyAndStatus(
        tenant: UUID,
        user: UUID,
        period: String,
        status: String,
    ): InvoiceEntity?

    fun findAllByStatus(status: String, pageable: Pageable): List<InvoiceEntity>

    fun findByStripeInvoiceId(id: String): InvoiceEntity?
}
