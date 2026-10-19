package fr.codetics.ev_server.services.audit

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.audit.AuditEventResponse
import fr.codetics.ev_server.repositories.AuditRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class AuditService(private val auditEvents: AuditRepository) {
    fun list(user: AppPrincipal): List<AuditEventResponse> =
        auditEvents.findAllByTenantIdOrderByIdDesc(user.tenantId, PageRequest.of(0, 200)).map {
            event ->
            AuditEventResponse(event.id, event.actor, event.action, event.target, event.createdAt)
        }
}
