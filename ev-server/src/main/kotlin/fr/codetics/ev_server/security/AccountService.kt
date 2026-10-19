package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.AccessTokenEntity
import fr.codetics.ev_server.models.entities.AuthChallengeEntity
import fr.codetics.ev_server.models.entities.MailEntity
import fr.codetics.ev_server.models.entities.OAuthIdentityEntity
import fr.codetics.ev_server.models.entities.OAuthIdentityKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.repositories.AccessTokenRepository
import fr.codetics.ev_server.repositories.AuthChallengeRepository
import fr.codetics.ev_server.repositories.MailRepository
import fr.codetics.ev_server.repositories.OAuthIdentityRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AccountService(
    private val db: Database,
    private val passwords: PasswordEncoder,
    private val users: UserRepository,
    private val tenants: TenantRepository,
    private val tokens: AccessTokenRepository,
    private val challenges: AuthChallengeRepository,
    private val identities: OAuthIdentityRepository,
    private val mail: MailRepository,
    private val rateLimiter: RateLimiter,
    @param:Value("\${app.token-hours}") private val tokenHours: Long,
    @param:Value("\${app.signup-enabled}") private val signupEnabled: Boolean,
    @param:Value("\${app.public-tenant}") private val publicTenant: String,
    @param:Value("\${app.public-url}") private val publicUrl: String,
) {
    private val dummyPassword = passwords.encode(randomToken())

    private fun principal(user: UserEntity): AppPrincipal {
        return AppPrincipal(
            id = user.id,
            tenantId = user.tenantId,
            email = user.email,
            name = user.displayName,
            role = user.role,
        )
    }

    fun findUser(id: UUID): AppPrincipal? {
        val user = users.findById(id).orElse(null) ?: return null
        val tenant = tenants.findById(user.tenantId).orElse(null) ?: return null

        if (!user.enabled || !tenant.active) {
            return null
        }

        return principal(user)
    }

    fun authenticateToken(raw: String): AppPrincipal? {
        if (raw.length !in 40..100) {
            return null
        }

        return authenticateTokenHash(sha256(raw))
    }

    fun authenticateTokenHash(hash: String): AppPrincipal? {
        val token = tokens.findById(hash).orElse(null) ?: return null

        if (!token.expiresAt.isAfter(Instant.now())) {
            return null
        }

        return findUser(token.userId)
    }

    fun limit(subject: String, maximum: Int = 10) {
        rateLimiter.check(subject, maximum)
    }

    fun login(tenant: String, email: String, password: String, address: String): AppPrincipal {
        require(password.length <= 1024 && email.length <= 320 && tenant.length <= 63)
        val normalizedEmail = email.trim().lowercase()
        limit("ip:$address", 100)
        limit("login:$tenant:$normalizedEmail")

        val organization = tenants.findBySlugAndActiveTrue(tenant)
        val user = organization?.let { users.findByTenantIdAndEmail(it.id, normalizedEmail) }
        val passwordMatches = passwords.matches(password, user?.passwordHash ?: dummyPassword)

        if (!passwordMatches || user == null || !user.enabled) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants incorrects.")
        }

        if (!user.emailVerified) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Vérifiez votre adresse électronique avant de vous connecter.",
            )
        }

        return principal(user)
    }

    fun issue(user: AppPrincipal): String {
        val raw = randomToken()
        val expiresAt = Instant.now().plusSeconds(tokenHours.coerceIn(1, 24) * 3600)

        tokens.save(
            AccessTokenEntity(tokenHash = sha256(raw), userId = user.id, expiresAt = expiresAt)
        )
        db.audit(user.tenantId, user.id.toString(), "AUTH_SIGN_IN", user.id.toString())

        return raw
    }

    fun revoke(raw: String) {
        tokens.deleteById(sha256(raw))
    }

    fun revokeAll(user: UUID) {
        db.atomic {
            tokens.deleteAllByUserId(user)
            true
        }
    }

    fun validatePassword(password: String) {
        require(password.length in 16..128) {
            "Le mot de passe doit contenir entre 16 et 128 caractères."
        }
    }

    fun register(email: String, name: String, password: String) {
        if (!signupEnabled || publicTenant.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "L’inscription libre est désactivée.",
            )
        }

        validatePassword(password)
        validateEmail(email)
        require(name.isNotBlank() && name.length <= 200)

        db.atomic {
            val tenant = tenantBySlug(publicTenant)
            val existing = users.findByTenantIdAndEmail(tenant, email.lowercase())

            if (existing == null) {
                val user =
                    users.save(
                        UserEntity(
                            tenantId = tenant,
                            email = email.lowercase(),
                            displayName = name,
                            passwordHash = passwords.encode(password),
                        )
                    )
                challenge(user, "VERIFY")
            }

            true
        }
    }

    fun requestChallenge(tenant: String, email: String, kind: String) {
        limit("challenge:$tenant:${email.lowercase()}", 5)
        val organization = tenants.findBySlugAndActiveTrue(tenant) ?: return
        val user = users.findByTenantIdAndEmail(organization.id, email.lowercase()) ?: return

        if (!user.enabled || (kind == "VERIFY" && user.emailVerified)) {
            return
        }

        db.atomic {
            challenge(user, kind)
            true
        }
    }

    private fun challenge(user: UserEntity, kind: String) {
        val raw = randomToken()
        val path = if (kind == "RESET") "reset-password" else "verify-email"
        val action =
            if (kind == "RESET") "Réinitialiser le mot de passe"
            else "Vérifier l’adresse électronique"

        challenges.deleteAllByUserIdAndKind(user.id, kind)
        challenges.save(
            AuthChallengeEntity(
                tokenHash = sha256(raw),
                userId = user.id,
                kind = kind,
                expiresAt = Instant.now().plusSeconds(1800),
            )
        )
        mail.save(
            MailEntity(
                recipient = user.email,
                subject = "Codetics Supervision — $action",
                body = "$publicUrl/auth/$path?token=$raw\n\nCe lien expire dans 30 minutes.",
            )
        )
    }

    fun completeChallenge(raw: String, kind: String, password: String? = null): Boolean {
        require(raw.length in 40..100)

        if (kind == "RESET") {
            validatePassword(requireNotNull(password))
        }

        return db.atomic {
            val challenge = challenges.findForConsumption(sha256(raw))

            if (
                challenge == null ||
                    challenge.kind != kind ||
                    !challenge.expiresAt.isAfter(Instant.now())
            ) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ce lien est invalide ou a expiré.",
                )
            }

            val user = users.findById(challenge.userId).orElseThrow()

            if (kind == "RESET") {
                user.passwordHash = passwords.encode(password)
                tokens.deleteAllByUserId(user.id)
            } else {
                user.emailVerified = true
            }

            users.save(user)
            challenges.delete(challenge)
            true
        }
    }

    fun tenantBySlug(slug: String): UUID {
        val tenant =
            tenants.findBySlugAndActiveTrue(slug)
                ?: throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "L’espace client est indisponible.",
                )

        return tenant.id
    }

    fun oauth(
        issuer: String,
        subject: String,
        email: String?,
        name: String?,
        verified: Boolean,
        linkUser: UUID?,
    ): AppPrincipal {
        return db.atomic {
            val key = OAuthIdentityKey(issuer, subject)
            val existing = identities.findById(key).orElse(null)

            if (existing != null) {
                if (linkUser != null && existing.userId != linkUser) {
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ce compte externe est déjà associé.",
                    )
                }

                return@atomic findUser(existing.userId)
                    ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Compte désactivé.")
            }

            val user =
                if (linkUser != null) {
                    findUser(linkUser) ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
                } else {
                    provisionOAuthUser(email, name, verified)
                }

            identities.save(OAuthIdentityEntity(key, user.id))
            db.audit(user.tenantId, user.id.toString(), "OAUTH_LINK", issuer)
            user
        }
    }

    private fun provisionOAuthUser(email: String?, name: String?, verified: Boolean): AppPrincipal {
        if (!signupEnabled || publicTenant.isBlank() || email.isNullOrBlank() || !verified) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Associez ce fournisseur depuis un compte existant ou activez l’inscription avec vérification.",
            )
        }

        validateEmail(email)
        val tenant = tenantBySlug(publicTenant)

        if (users.findByTenantIdAndEmail(tenant, email.lowercase()) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Connectez-vous à votre compte existant pour associer ce fournisseur.",
            )
        }

        val user =
            users.save(
                UserEntity(
                    tenantId = tenant,
                    email = email.lowercase(),
                    displayName = (name ?: email).take(200),
                    emailVerified = true,
                )
            )

        return principal(user)
    }
}

fun validateEmail(email: String) {
    require(email.length <= 320 && email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
        "Renseignez une adresse électronique valide."
    }
}

@Service
class Bootstrap(
    private val db: Database,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    @param:Value("\${app.bootstrap.email}") private val email: String,
    @param:Value("\${app.bootstrap.password}") private val password: String,
    @param:Value("\${app.bootstrap.tenant}") private val slug: String,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (email.isBlank()) {
            return
        }

        accounts.validatePassword(password)
        validateEmail(email)
        require(slug.matches(Regex("[a-z0-9][a-z0-9-]{0,62}")))

        db.atomic {
            if (users.count() == 0L) {
                val tenant =
                    tenants.findBySlugAndActiveTrue(slug)
                        ?: tenants.save(TenantEntity(slug = slug, name = "Codetics"))

                users.save(
                    UserEntity(
                        tenantId = tenant.id,
                        email = email.trim().lowercase(),
                        displayName = "Administrateur",
                        passwordHash = passwords.encode(password),
                        role = "ADMIN",
                        emailVerified = true,
                    )
                )
            }

            true
        }
    }
}
