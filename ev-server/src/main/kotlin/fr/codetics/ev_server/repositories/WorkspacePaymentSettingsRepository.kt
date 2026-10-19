package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface WorkspacePaymentSettingsRepository : JpaRepository<WorkspacePaymentSettings, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select settings from WorkspacePaymentSettings settings where settings.tenantId = :tenantId"
    )
    fun findForUpdate(tenantId: UUID): WorkspacePaymentSettings?
}
