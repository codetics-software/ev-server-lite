package fr.codetics.ev_server.services.workspaces

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.workspaces.PlatformWorkspaceInput
import fr.codetics.ev_server.models.workspaces.PlatformWorkspaceResponse
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.anyOf
import fr.codetics.ev_server.persistence.contains
import fr.codetics.ev_server.persistence.count
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.page
import fr.codetics.ev_server.repositories.TenantRepository
import java.util.UUID
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class PlatformService(
    private val db: Database,
    private val tenants: TenantRepository,
    private val queries: Queries,
    private val workspaces: WorkspaceService,
) {
    fun list(user: AppPrincipal, search: String, offset: Int, limit: Int): PageResponse<PlatformWorkspaceResponse> {
        authorize(user)
        val conditions =
            if (search.isBlank()) emptyList()
            else listOf(anyOf(contains("name", search.trim()), contains("slug", search.trim())))
        val page =
            queries.page<TenantEntity>(conditions, pageRequest(offset, limit, Sort.by("name", "slug")))
        return PageResponse(page.content.map(::response), page.totalElements)
    }

    fun update(user: AppPrincipal, id: UUID, input: PlatformWorkspaceInput): PlatformWorkspaceResponse {
        authorize(user)
        ensure(input.name.isNotBlank() && input.name.length <= 200) {
            "Le nom de l’espace doit contenir entre 1 et 200 caractères."
        }
        ensure(id != user.tenantId || input.active) {
            "Vous ne pouvez pas désactiver l’espace de la plateforme."
        }

        return db.atomic {
            val tenant = tenants.findById(id).orElse(null) ?: throw NotFound("Espace introuvable.")
            tenant.name = input.name.trim()
            tenant.active = input.active
            tenant.components = db.encode(input.components.toComponents())
            tenants.save(tenant)
            db.audit(user.tenantId, user.id.toString(), "PLATFORM_WORKSPACE_UPDATE", tenant.slug)
            response(tenant)
        }
    }

    private fun response(tenant: TenantEntity) =
        PlatformWorkspaceResponse(
            tenant.id,
            tenant.slug,
            tenant.name,
            tenant.active,
            tenant.createdAt,
            workspaces.components(tenant),
            queries.count<UserEntity>(listOf(eq("tenantId", tenant.id), eq("deletedAt", null))),
            queries.count<StationEntity>(listOf(eq("key.tenantId", tenant.id), eq("deleted", false))),
        )

    private fun authorize(user: AppPrincipal) {
        if (!user.platformAdmin) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs de la plateforme.")
        }
    }
}
