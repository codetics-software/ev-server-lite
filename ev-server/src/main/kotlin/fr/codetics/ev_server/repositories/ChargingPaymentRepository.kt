package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import java.util.UUID
import org.springframework.data.domain.Pageable

interface ChargingPaymentRepository : StoreRepository<ChargingPaymentEntity, UUID> {
    fun findByTenantIdAndTransactionId(tenantId: UUID, transactionId: Int): ChargingPaymentEntity?

    fun findByTenantIdAndTokenUid(tenantId: UUID, tokenUid: String): ChargingPaymentEntity?

    fun findByTenantIdAndRequestId(tenantId: UUID, requestId: UUID): ChargingPaymentEntity?

    fun findAllByTenantIdOrderByCreatedAtDesc(
        tenantId: UUID,
        pageable: Pageable,
    ): List<ChargingPaymentEntity>

    fun existsByEvseIdAndStateIn(evseId: UUID, states: Collection<PaymentState>): Boolean

    fun findAllByStateIn(
        states: Collection<PaymentState>,
        pageable: Pageable,
    ): List<ChargingPaymentEntity>
}
