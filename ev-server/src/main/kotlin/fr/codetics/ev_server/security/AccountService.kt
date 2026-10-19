package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.AccessTokenEntity
import fr.codetics.ev_server.models.entities.AuthChallengeEntity
import fr.codetics.ev_server.models.entities.MailEntity
import fr.codetics.ev_server.models.entities.OAuthIdentityEntity
import fr.codetics.ev_server.models.entities.OAuthIdentityKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.ChallengeKind
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.AccessTokenRepository
import fr.codetics.ev_server.repositories.AuthChallengeRepository
import fr.codetics.ev_server.repositories.MailRepository
import fr.codetics.ev_server.repositories.OAuthIdentityRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
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
    private val properties: AppProperties,
    private val clock: Clock,
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

        if (!token.expiresAt.isAfter(Instant.now(clock))) {
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
        val lifetime = Duration.ofHours(properties.tokenHours.coerceIn(1, 24))
        val expiresAt = Instant.now(clock).plus(lifetime)

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
        }
    }

    fun validatePassword(password: String) {
        ensure(password.length in 16..128) {
            "Le mot de passe doit contenir entre 16 et 128 caractères."
        }
    }

    fun register(email: String, name: String, password: String) {
        if (!properties.signupEnabled || properties.publicTenant.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "L’inscription libre est désactivée.",
            )
        }

        validatePassword(password)
        validateEmail(email)
        require(name.isNotBlank() && name.length <= 200)

        db.atomic {
            val tenant = tenantBySlug(properties.publicTenant)
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
                challenge(user, ChallengeKind.VERIFY)
            }
        }
    }

    fun requestChallenge(tenant: String, email: String, kind: ChallengeKind) {
        limit("challenge:$tenant:${email.lowercase()}", 5)
        val organization = tenants.findBySlugAndActiveTrue(tenant) ?: return
        val user = users.findByTenantIdAndEmail(organization.id, email.lowercase()) ?: return

        if (!user.enabled || (kind == ChallengeKind.VERIFY && user.emailVerified)) {
            return
        }

        db.atomic {
            challenge(user, kind)
        }
    }

    private fun challenge(user: UserEntity, kind: ChallengeKind) {
        val raw = randomToken()
        val (path, action) =
            when (kind) {
                ChallengeKind.RESET -> "reset-password" to "Réinitialiser le mot de passe"
                ChallengeKind.VERIFY -> "verify-email" to "Vérifier l’adresse électronique"
            }

        challenges.deleteAllByUserIdAndKind(user.id, kind)
        challenges.save(
            AuthChallengeEntity(
                tokenHash = sha256(raw),
                userId = user.id,
                kind = kind,
                expiresAt = Instant.now(clock).plus(CHALLENGE_TTL),
            )
        )
        mail.save(
            MailEntity(
                recipient = user.email,
                subject = "Codetics Supervision — $action",
                body =
                    "${properties.publicUrl}/auth/$path?token=$raw\n\nCe lien expire dans 30 minutes.",
            )
        )
    }

    fun completeChallenge(raw: String, kind: ChallengeKind, password: String? = null): Boolean {
        require(raw.length in 40..100)

        if (kind == ChallengeKind.RESET) {
            validatePassword(requireNotNull(password))
        }

        return db.atomic {
            val challenge = db.lock<AuthChallengeEntity>(sha256(raw))

            if (
                challenge == null ||
                    challenge.kind != kind ||
                    !challenge.expiresAt.isAfter(Instant.now(clock))
            ) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ce lien est invalide ou a expiré.",
                )
            }

            val user = users.findById(challenge.userId).orElseThrow()

            if (kind == ChallengeKind.RESET) {
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
        if (
            !properties.signupEnabled ||
                properties.publicTenant.isBlank() ||
                email.isNullOrBlank() ||
                !verified
        ) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Associez ce fournisseur depuis un compte existant ou activez l’inscription avec vérification.",
            )
        }

        validateEmail(email)
        val tenant = tenantBySlug(properties.publicTenant)

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

private val CHALLENGE_TTL: Duration = Duration.ofMinutes(30)
private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val TENANT_SLUG = Regex("[a-z0-9][a-z0-9-]{0,62}")

fun validateEmail(email: String) {
    ensure(email.length <= 320 && email.matches(EMAIL)) {
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
    private val properties: AppProperties,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val (email, password, slug) = properties.bootstrap
        if (email.isBlank()) {
            return
        }

        accounts.validatePassword(password)
        validateEmail(email)
        require(slug.matches(TENANT_SLUG))

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
                        role = UserRole.ADMIN,
                        emailVerified = true,
                    )
                )
            }
        }
    }
}
