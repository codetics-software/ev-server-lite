package fr.codetics.ev_server.services.sessions

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.sessions.ChargingSessionResponse
import fr.codetics.ev_server.repositories.TransactionRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class ChargingSessionService(private val sessions: TransactionRepository) {
    fun list(user: AppPrincipal, offset: Int, limit: Int): PageResponse<ChargingSessionResponse> {
        val request = pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "startedAt", "id"))
        val page =
            if (user.role == UserRole.DRIVER) {
                sessions.findAllByTenantIdAndUserId(user.tenantId, user.id, request)
            } else {
                sessions.findAllByTenantId(user.tenantId, request)
            }
        return PageResponse(page.content.map(ChargingSessionResponse::from), page.totalElements)
    }
}
