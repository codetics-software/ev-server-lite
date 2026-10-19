package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ChargingPaymentRepository : JpaRepository<ChargingPaymentEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from ChargingPaymentEntity payment where payment.id = :id")
    fun findForUpdate(id: UUID): ChargingPaymentEntity?

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
