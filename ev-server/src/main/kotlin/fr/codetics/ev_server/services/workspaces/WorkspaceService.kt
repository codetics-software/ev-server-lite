package fr.codetics.ev_server.services.workspaces

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.workspaces.WorkspaceCreation
import fr.codetics.ev_server.models.workspaces.WorkspaceResponse
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.security.validateEmail
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${app.workspace-signup-enabled:false}") private val signupEnabled: Boolean,
    @Value("\${app.mail-enabled:false}") private val mailEnabled: Boolean,
) {
    fun registrationAvailable() = signupEnabled && mailEnabled

    fun current(tenantId: UUID): WorkspaceResponse {
        val tenant = tenants.findById(tenantId).orElseThrow()
        return WorkspaceResponse(tenant.id, tenant.slug, tenant.name, tenant.active)
    }

    fun rename(user: AppPrincipal, name: String): WorkspaceResponse =
        db.atomic {
            require(name.isNotBlank() && name.length <= 200) {
                "Le nom de l’espace doit contenir entre 1 et 200 caractères."
            }
            val tenant = tenants.findById(user.tenantId).orElseThrow()
            tenant.name = name.trim()
            tenants.save(tenant)
            db.audit(user.tenantId, user.id.toString(), "WORKSPACE_UPDATE", tenant.slug)
            current(user.tenantId)
        }

    fun create(input: WorkspaceCreation): WorkspaceResponse {
        if (!signupEnabled || !mailEnabled)
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "La création publique d’espaces n’est pas activée.",
            )
        require(input.slug.matches(Regex("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]"))) {
            "L’identifiant doit contenir de 3 à 63 lettres minuscules, chiffres ou tirets."
        }
        require(input.name.isNotBlank() && input.name.length <= 200) {
            "Le nom de l’espace est requis."
        }
        require(input.administratorName.isNotBlank() && input.administratorName.length <= 200) {
            "Le nom de l’administrateur est requis."
        }
        require(input.password.length in 16..128) {
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
                    tenants.saveAndFlush(TenantEntity(slug = input.slug, name = input.name.trim()))
                users.saveAndFlush(
                    UserEntity(
                        tenantId = tenant.id,
                        email = input.email.trim().lowercase(),
                        displayName = input.administratorName.trim(),
                        passwordHash = passwords.encode(input.password),
                        role = "ADMIN",
                        emailVerified = false,
                    )
                )
                current(tenant.id)
            }
        accounts.requestChallenge(workspace.slug, input.email, "VERIFY")
        return workspace
    }
}
