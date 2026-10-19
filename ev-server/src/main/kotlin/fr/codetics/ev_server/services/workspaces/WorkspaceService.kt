package fr.codetics.ev_server.services.workspaces

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.ChallengeKind
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.workspaces.WorkspaceCreation
import fr.codetics.ev_server.models.workspaces.WorkspaceResponse
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
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
) {
    fun registrationAvailable() = properties.workspaceSignupEnabled && properties.mailEnabled

    fun current(tenantId: UUID): WorkspaceResponse {
        val tenant = tenants.findById(tenantId).orElseThrow()
        return WorkspaceResponse(tenant.id, tenant.slug, tenant.name, tenant.active)
    }

    fun rename(user: AppPrincipal, name: String): WorkspaceResponse =
        db.atomic {
            ensure(name.isNotBlank() && name.length <= 200) {
                "Le nom de l’espace doit contenir entre 1 et 200 caractères."
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
                "La création publique d’espaces n’est pas activée.",
            )
        ensure(input.slug.matches(Regex("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]"))) {
            "L’identifiant doit contenir de 3 à 63 lettres minuscules, chiffres ou tirets."
        }
        ensure(input.name.isNotBlank() && input.name.length <= 200) {
            "Le nom de l’espace est requis."
        }
        ensure(input.administratorName.isNotBlank() && input.administratorName.length <= 200) {
            "Le nom de l’administrateur est requis."
        }
        ensure(input.password.length in 16..128) {
            "Le mot de passe doit contenir entre 16 et 128 caractères."
        }
        validateEmail(input.email)
        val workspace =
            db.atomic {
                if (tenants.findBySlugAndActiveTrue(input.slug) != null)
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cet identifiant d’espace est déjà utilisé.",
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
