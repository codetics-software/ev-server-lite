package fr.codetics.ev_server.services.sessions

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.sessions.ChargingSessionResponse
import fr.codetics.ev_server.repositories.TransactionRepository
import java.util.UUID
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

@Service
class ChargingSessionService(private val sessions: TransactionRepository) {
    fun list(user: AppPrincipal, offset: Int, limit: Int): PageResponse<ChargingSessionResponse> {
        val page =
            sessions.findAll(
                visibleTo(user),
                pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "startedAt", "id")),
            )
        return PageResponse(page.content.map(ChargingSessionResponse::from), page.totalElements)
    }

    private fun visibleTo(user: AppPrincipal) =
        Specification<TransactionEntity> { root, _, criteria ->
            val tenant = criteria.equal(root.get<UUID>("tenantId"), user.tenantId)
            if (user.role == "DRIVER") {
                criteria.and(tenant, criteria.equal(root.get<UUID>("userId"), user.id))
            } else {
                tenant
            }
        }
}
