package fr.codetics.ev_server.services.people

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.CreatedUser
import fr.codetics.ev_server.models.people.UserInput
import fr.codetics.ev_server.models.people.UserResponse
import fr.codetics.ev_server.repositories.AccessTokenRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.validateEmail
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val db: Database,
    private val users: UserRepository,
    private val accessTokens: AccessTokenRepository,
) {
    fun list(user: AppPrincipal): List<UserResponse> =
        users
            .findAllByTenantId(user.tenantId, PageRequest.of(0, 500, Sort.by("email")))
            .content
            .map { account ->
                UserResponse(
                    account.id,
                    account.email,
                    account.displayName,
                    account.role,
                    account.enabled,
                    account.emailVerified,
                )
            }

    fun create(body: UserInput, user: AppPrincipal): CreatedUser {
        validateUser(body)

        return db.atomic {
            if (users.findByTenantIdAndEmail(user.tenantId, body.email.lowercase()) != null) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cette adresse électronique existe déjà.",
                )
            }
            val account =
                users.save(
                    UserEntity(
                        tenantId = user.tenantId,
                        email = body.email.lowercase(),
                        displayName = body.name,
                        role = body.role,
                        enabled = body.enabled,
                    )
                )
            db.audit(user.tenantId, user.id.toString(), "USER_CREATE", account.id.toString())
            CreatedUser(account.id)
        }
    }

    fun update(id: UUID, body: UserInput, user: AppPrincipal): MutationResponse {
        validateUser(body)
        require(id != user.id || (body.enabled && body.role == "ADMIN")) {
            "Vous ne pouvez pas désactiver votre propre compte administrateur ni réduire ses droits."
        }

        return db.atomic {
            val account =
                users.findByTenantIdAndId(user.tenantId, id)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

            account.displayName = body.name
            account.role = body.role
            account.enabled = body.enabled
            users.save(account)
            accessTokens.deleteAllByUserId(id)
            db.audit(user.tenantId, user.id.toString(), "USER_UPDATE", id.toString())
            MutationResponse()
        }
    }

    private fun validateUser(body: UserInput) {
        validateEmail(body.email)
        require(body.name.isNotBlank() && body.name.length <= 200)
        require(body.role in setOf("ADMIN", "OPERATOR", "VIEWER", "DRIVER"))
    }
}
