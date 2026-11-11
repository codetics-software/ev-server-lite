package fr.codetics.ev_server.services.workspaces

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.ChallengeKind
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.entities.WorkspaceLogoEntity
import fr.codetics.ev_server.models.workspaces.WorkspaceComponents
import fr.codetics.ev_server.models.workspaces.WorkspaceComponentsInput
import fr.codetics.ev_server.models.workspaces.WorkspaceCreation
import fr.codetics.ev_server.models.workspaces.WorkspaceResponse
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.repositories.WorkspaceLogoRepository
import java.time.Instant
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.security.validateEmail
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class WorkspaceService(
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val db: Database,
    private val properties: AppProperties,
    private val logos: WorkspaceLogoRepository,
) {
    fun registrationAvailable() = properties.workspaceSignupEnabled && properties.mailEnabled

    fun current(tenantId: UUID): WorkspaceResponse = response(tenants.findById(tenantId).orElseThrow())

    fun response(tenant: TenantEntity) =
        WorkspaceResponse(
            tenant.id,
            tenant.slug,
            tenant.name,
            tenant.active,
            components(tenant),
            if (tenant.hasLogo) {
                val version = logos.findById(tenant.id).map { it.updatedAt.toEpochMilli() }.orElse(0)
                "/api/v1/public/workspaces/${tenant.slug}/logo?v=$version"
            } else {
                null
            },
        )

    fun components(tenant: TenantEntity): WorkspaceComponents =
        runCatching { db.json.readValue(tenant.components, WorkspaceComponents::class.java) }
            .getOrDefault(WorkspaceComponents())

    fun components(tenantId: UUID): WorkspaceComponents =
        tenants.findById(tenantId).map(::components).orElse(WorkspaceComponents())

    fun updateComponents(user: AppPrincipal, input: WorkspaceComponentsInput): WorkspaceResponse =
        db.atomic {
            val tenant = tenants.findById(user.tenantId).orElseThrow()
            tenant.components = db.encode(input.toComponents())
            tenants.save(tenant)
            db.audit(user.tenantId, user.id.toString(), "WORKSPACE_COMPONENTS", tenant.slug)
            response(tenant)
        }

    fun uploadLogo(user: AppPrincipal, contentType: String?, content: ByteArray): WorkspaceResponse {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        ensure(type in LOGO_TYPES) { msg("workspaces.logo_format") }
        ensure(content.size in 1..LOGO_LIMIT) { msg("workspaces.logo_size") }
        ensure(matchesSignature(requireNotNull(type), content)) {
            msg("files.content_mismatch")
        }

        return db.atomic {
            val tenant = tenants.findById(user.tenantId).orElseThrow()
            logos.save(WorkspaceLogoEntity(tenant.id, type, content, Instant.now()))
            tenant.hasLogo = true
            tenants.save(tenant)
            db.audit(user.tenantId, user.id.toString(), "WORKSPACE_LOGO", tenant.slug)
            response(tenant)
        }
    }

    fun deleteLogo(user: AppPrincipal): WorkspaceResponse =
        db.atomic {
            val tenant = tenants.findById(user.tenantId).orElseThrow()
            logos.deleteById(tenant.id)
            tenant.hasLogo = false
            tenants.save(tenant)
            db.audit(user.tenantId, user.id.toString(), "WORKSPACE_LOGO_DELETE", tenant.slug)
            response(tenant)
        }

    fun logo(slug: String): WorkspaceLogoEntity? {
        val tenant = tenants.findBySlugAndActiveTrue(slug) ?: return null
        return logos.findById(tenant.id).orElse(null)
    }

    private fun matchesSignature(type: String, content: ByteArray): Boolean {
        fun starts(vararg bytes: Int) =
            content.size >= bytes.size && bytes.indices.all { content[it] == bytes[it].toByte() }
        return when (type) {
            "image/png" -> starts(0x89, 0x50, 0x4E, 0x47)
            "image/jpeg" -> starts(0xFF, 0xD8, 0xFF)
            "image/webp" ->
                starts(0x52, 0x49, 0x46, 0x46) &&
                    content.size > 12 &&
                    String(content, 8, 4, Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }

    fun rename(user: AppPrincipal, name: String): WorkspaceResponse =
        db.atomic {
            ensure(name.isNotBlank() && name.length <= 200) {
                msg("workspaces.name_length")
            }
            val tenant = tenants.findById(user.tenantId).orElseThrow()
            tenant.name = name.trim()
            tenants.save(tenant)
            db.audit(user.tenantId, user.id.toString(), "WORKSPACE_UPDATE", tenant.slug)
            current(user.tenantId)
        }

    fun create(input: WorkspaceCreation): WorkspaceResponse {
        if (!registrationAvailable())
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                msg("workspaces.signup_disabled"),
            )
        ensure(input.slug.matches(Regex("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]"))) {
            msg("workspaces.slug_format")
        }
        ensure(input.name.isNotBlank() && input.name.length <= 200) {
            msg("workspaces.name_required")
        }
        ensure(input.administratorName.isNotBlank() && input.administratorName.length <= 200) {
            msg("workspaces.admin_name_required")
        }
        ensure(input.password.length in 16..128) {
            msg("auth.password_length")
        }
        validateEmail(input.email)
        val workspace =
            db.atomic {
                if (tenants.findBySlugAndActiveTrue(input.slug) != null)
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        msg("workspaces.slug_taken"),
                    )
                val tenant =
                    tenants.save(TenantEntity(slug = input.slug, name = input.name.trim())).also {
                        db.flush()
                    }
                users.save(
                    UserEntity(
                        tenantId = tenant.id,
                        email = input.email.trim().lowercase(),
                        displayName = input.administratorName.trim(),
                        passwordHash = passwords.encode(input.password),
                        role = UserRole.ADMIN,
                        emailVerified = false,
                    )
                ).also { db.flush() }
                current(tenant.id)
            }
        accounts.requestChallenge(workspace.slug, input.email, ChallengeKind.VERIFY)
        return workspace
    }
}

private val LOGO_TYPES = setOf("image/png", "image/jpeg", "image/webp")
private const val LOGO_LIMIT = 262144
