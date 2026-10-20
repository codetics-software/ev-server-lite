package fr.codetics.ev_server.services.people

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Csv
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ImportError
import fr.codetics.ev_server.core.ImportResult
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.SiteUserEntity
import fr.codetics.ev_server.models.entities.SiteUserKey
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.notifications.NotificationPreferences
import fr.codetics.ev_server.models.notifications.NotificationPreferencesInput
import fr.codetics.ev_server.models.people.ChallengeKind
import fr.codetics.ev_server.models.people.CreatedUser
import fr.codetics.ev_server.models.people.ProfileInput
import fr.codetics.ev_server.models.people.SiteAssignmentInput
import fr.codetics.ev_server.models.people.SiteUserResponse
import fr.codetics.ev_server.models.people.UserInError
import fr.codetics.ev_server.models.people.UserInput
import fr.codetics.ev_server.models.people.UserResponse
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.people.UserSiteResponse
import fr.codetics.ev_server.persistence.Condition
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.anyOf
import fr.codetics.ev_server.persistence.contains
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.persistence.page
import fr.codetics.ev_server.repositories.AccessTokenRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.OAuthIdentityRepository
import fr.codetics.ev_server.repositories.SiteUserRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.security.EULA_VERSION
import fr.codetics.ev_server.security.validateEmail
import fr.codetics.ev_server.services.organization.Organization
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

data class UserFilter(val search: String = "", val role: UserRole? = null, val state: String? = null)

@Service
class UserService(
    private val db: Database,
    private val users: UserRepository,
    private val accessTokens: AccessTokenRepository,
    private val identities: OAuthIdentityRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val siteUsers: SiteUserRepository,
    private val tenants: TenantRepository,
    private val paymentSettings: WorkspacePaymentSettingsRepository,
    private val organization: Organization,
    private val accounts: AccountService,
    private val queries: Queries,
    private val properties: AppProperties,
    private val events: ApplicationEventPublisher,
) {
    fun list(user: AppPrincipal, offset: Int = 0, limit: Int = 500, filter: UserFilter = UserFilter()): PageResponse<UserResponse> {
        val page =
            queries.page<UserEntity>(conditions(user.tenantId, filter), pageRequest(offset, limit, Sort.by("displayName", "email")))
        return PageResponse(responses(user.tenantId, page.content), page.totalElements)
    }

    private fun conditions(tenant: UUID, filter: UserFilter): List<Condition> {
        ensure(filter.search.length <= 200) { "La recherche est trop longue." }
        val conditions = mutableListOf<Condition>(eq("tenantId", tenant), eq("deletedAt", null))
        if (filter.search.isNotBlank()) {
            conditions += anyOf(contains("email", filter.search.trim()), contains("displayName", filter.search.trim()))
        }
        filter.role?.let { conditions += eq("role", it) }
        when (filter.state) {
            "active" -> conditions += listOf(eq("enabled", true), eq("emailVerified", true))
            "disabled" -> conditions += eq("enabled", false)
            "unverified" -> conditions += eq("emailVerified", false)
            null, "" -> Unit
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtre d’état inconnu.")
        }
        return conditions
    }

    private fun responses(tenant: UUID, accounts: List<UserEntity>): List<UserResponse> {
        val counts = siteUsers.findAllByKeyTenantId(tenant).groupingBy { it.key.userId }.eachCount()
        return accounts.map { UserResponse.from(it, counts[it.id] ?: 0, EULA_VERSION) }
    }

    fun find(user: AppPrincipal, id: UUID): UserResponse = responses(user.tenantId, listOf(account(user.tenantId, id))).first()

    fun account(tenant: UUID, id: UUID): UserEntity =
        users.findByTenantIdAndId(tenant, id)?.takeIf { it.deletedAt == null } ?: throw NotFound("Utilisateur introuvable.")

    fun create(body: UserInput, user: AppPrincipal): CreatedUser {
        validateUser(body.email, body.name, body.phone, body.locale)

        val created =
            db.atomic {
                if (users.findByTenantIdAndEmail(user.tenantId, body.email.trim().lowercase()) != null) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse électronique existe déjà.")
                }
                val account =
                    users.save(
                        UserEntity(
                            tenantId = user.tenantId,
                            email = body.email.trim().lowercase(),
                            displayName = body.name.trim(),
                            role = body.role,
                            enabled = body.enabled,
                            phone = body.phone?.trim()?.ifBlank { null },
                            locale = body.locale ?: "fr",
                        )
                    ).also { db.flush() }
                assignAutomaticSites(account)
                db.audit(user.tenantId, user.id.toString(), "USER_CREATE", account.id.toString())
                account
            }
        invite(created)
        return CreatedUser(created.id)
    }

    fun invite(account: UserEntity) {
        val slug = tenants.findById(account.tenantId).orElse(null)?.slug ?: return
        runCatching { accounts.requestChallenge(slug, account.email, ChallengeKind.RESET) }
    }

    @EventListener
    fun registered(event: UserRegistered) = assignAutomaticSites(event.user)

    fun assignAutomaticSites(account: UserEntity) {
        organization.sites(account.tenantId).filter { it.data.autoUserAssignment }.forEach { site ->
            val key = SiteUserKey(account.tenantId, site.id, account.id)
            if (!siteUsers.existsById(key)) siteUsers.save(SiteUserEntity(key))
        }
    }

    fun update(id: UUID, body: UserInput, user: AppPrincipal): MutationResponse {
        validateUser(body.email, body.name, body.phone, body.locale)
        ensure(id != user.id || (body.enabled && body.role == UserRole.ADMIN)) {
            "Vous ne pouvez pas désactiver votre propre compte administrateur ni réduire ses droits."
        }

        return db.atomic {
            val account = account(user.tenantId, id)
            val statusChanged = account.enabled != body.enabled
            account.displayName = body.name.trim()
            account.role = body.role
            account.enabled = body.enabled
            account.phone = body.phone?.trim()?.ifBlank { null }
            body.locale?.let { account.locale = it }
            users.save(account)
            accessTokens.deleteAllByUserId(id)
            db.audit(user.tenantId, user.id.toString(), "USER_UPDATE", id.toString())
            if (statusChanged) events.publishEvent(UserStatusChanged(account, account.enabled))
            MutationResponse()
        }
    }

    fun delete(id: UUID, user: AppPrincipal): MutationResponse {
        ensure(id != user.id) { "Vous ne pouvez pas supprimer votre propre compte." }
        return db.atomic {
            val account = account(user.tenantId, id)
            chargingTokens.findAllByKeyTenantIdAndUserId(user.tenantId, id).forEach { token ->
                token.userId = null
                token.enabled = false
                token.defaultToken = false
                chargingTokens.save(token)
            }
            siteUsers.deleteAll(siteUsers.findAllByKeyTenantIdAndKeyUserId(user.tenantId, id))
            accessTokens.deleteAllByUserId(id)
            identities.deleteAllByUserId(id)
            account.email = "supprime-${account.id}@invalid.local"
            account.displayName = "Utilisateur supprimé"
            account.passwordHash = null
            account.phone = null
            account.enabled = false
            account.emailVerified = false
            account.notificationPreferences = "{}"
            account.deletedAt = Instant.now()
            users.save(account)
            db.audit(user.tenantId, user.id.toString(), "USER_DELETE", id.toString())
            MutationResponse()
        }
    }

    fun inError(user: AppPrincipal): List<UserInError> {
        val accounts = queries.list<UserEntity>(conditions(user.tenantId, UserFilter()), Sort.by("displayName"))
        val withBadge =
            chargingTokens.findAllByKeyTenantId(user.tenantId, org.springframework.data.domain.PageRequest.of(0, 100000))
                .content
                .mapNotNull { it.userId }
                .toSet()
        val billing = paymentSettings.findById(user.tenantId).map { it.userBillingEnabled }.orElse(false)
        val inactiveBefore = Instant.now().minus(properties.supervision.inactivityMonths * 30, ChronoUnit.DAYS)
        val failing =
            accounts.mapNotNull { account ->
                val errors = buildList {
                    if (!account.emailVerified) add("UNVERIFIED")
                    if (!account.enabled) add("DISABLED")
                    if (account.role == UserRole.DRIVER && account.id !in withBadge) add("NO_BADGE")
                    if ((account.lastLoginAt ?: account.createdAt).isBefore(inactiveBefore)) add("INACTIVE")
                    if (billing && account.role == UserRole.DRIVER && account.stripeCustomerId == null) add("NO_PAYMENT_METHOD")
                }
                if (errors.isEmpty()) null else account to errors
            }
        val views = responses(user.tenantId, failing.map { it.first })
        return views.zip(failing).map { (view, entry) -> UserInError(view, entry.second) }
    }

    fun export(user: AppPrincipal) =
        Csv.download(
            "utilisateurs",
            listOf("email", "nom", "role", "actif", "verifie", "telephone", "langue", "cree_le", "derniere_connexion", "badges"),
            queries.list<UserEntity>(conditions(user.tenantId, UserFilter()), Sort.by("displayName")).asSequence().map { account ->
                listOf(
                    account.email,
                    account.displayName,
                    account.role,
                    if (account.enabled) "oui" else "non",
                    if (account.emailVerified) "oui" else "non",
                    account.phone,
                    account.locale,
                    account.createdAt,
                    account.lastLoginAt,
                    chargingTokens.findAllByKeyTenantIdAndUserId(user.tenantId, account.id).joinToString(" ") { it.key.uid },
                )
            },
        )

    fun import(user: AppPrincipal, content: String): ImportResult {
        val rows = Csv.read(content, 5000)
        var created = 0
        var skipped = 0
        val errors = mutableListOf<ImportError>()
        val invitations = mutableListOf<UserEntity>()
        rows.forEachIndexed { index, row ->
            val line = index + 2
            try {
                val email = row["email"].orEmpty().trim().lowercase()
                val name = (row["name"] ?: row["nom"]).orEmpty().trim().ifBlank { email }
                val role = (row["role"] ?: row["rôle"]).orEmpty().trim().uppercase().ifBlank { "DRIVER" }
                val badge = (row["badge"] ?: row["uid"]).orEmpty().trim()
                validateUser(email, name, null, null)
                val parsedRole =
                    runCatching { UserRole.valueOf(role) }.getOrNull()
                        ?: throw fr.codetics.ev_server.core.InvalidRequest("Rôle inconnu : $role.")
                ensure(badge.isEmpty() || badge.matches(Regex("[A-Za-z0-9_-]{1,20}"))) { "Badge invalide : $badge." }
                db.atomic {
                    if (users.findByTenantIdAndEmail(user.tenantId, email) != null) {
                        skipped += 1
                        return@atomic
                    }
                    val account =
                        users.save(UserEntity(tenantId = user.tenantId, email = email, displayName = name, role = parsedRole)).also {
                            db.flush()
                        }
                    if (badge.isNotEmpty()) {
                        val key = ChargingTokenKey(user.tenantId, badge)
                        val token = chargingTokens.findById(key).orElse(null)
                        ensure(token == null || token.userId == null) { "Le badge $badge est déjà attribué." }
                        chargingTokens.save((token ?: ChargingTokenEntity(key)).apply { userId = account.id })
                    }
                    assignAutomaticSites(account)
                    invitations += account
                    created += 1
                }
            } catch (error: IllegalArgumentException) {
                errors += ImportError(line, error.message ?: "Ligne invalide.")
            } catch (error: ResponseStatusException) {
                errors += ImportError(line, error.reason ?: "Ligne invalide.")
            }
        }
        db.audit(user.tenantId, user.id.toString(), "USERS_IMPORT", "$created")
        invitations.forEach(::invite)
        return ImportResult(created = created, skipped = skipped, errors = errors)
    }

    fun sites(user: AppPrincipal, id: UUID): List<UserSiteResponse> {
        account(user.tenantId, id)
        val sites = organization.sites(user.tenantId).associateBy { it.id }
        return siteUsers.findAllByKeyTenantIdAndKeyUserId(user.tenantId, id).mapNotNull { assignment ->
            sites[assignment.key.siteId]?.let {
                UserSiteResponse(it.id, it.name, assignment.siteAdmin, assignment.siteOwner)
            }
        }.sortedBy { it.name }
    }

    fun assignSites(user: AppPrincipal, id: UUID, input: List<SiteAssignmentInput>): List<UserSiteResponse> {
        account(user.tenantId, id)
        val sites = organization.sites(user.tenantId).map { it.id }.toSet()
        ensure(input.all { it.siteId != null && it.siteId in sites }) { "Un des sites sélectionnés n’existe pas." }
        db.atomic {
            siteUsers.deleteAll(siteUsers.findAllByKeyTenantIdAndKeyUserId(user.tenantId, id))
            db.flush()
            input.distinctBy { it.siteId }.forEach {
                siteUsers.save(SiteUserEntity(SiteUserKey(user.tenantId, requireNotNull(it.siteId), id), it.siteAdmin, it.siteOwner))
            }
            db.audit(user.tenantId, user.id.toString(), "USER_SITES", id.toString())
        }
        return sites(user, id)
    }

    fun siteUsers(user: AppPrincipal, siteId: UUID): List<SiteUserResponse> {
        ensure(organization.site(user.tenantId, siteId) != null) { "Site introuvable." }
        val assignments = siteUsers.findAllByKeyTenantIdAndKeySiteId(user.tenantId, siteId)
        val accounts = users.findAllByTenantIdAndIdIn(user.tenantId, assignments.map { it.key.userId }).associateBy { it.id }
        return assignments.mapNotNull { assignment ->
            accounts[assignment.key.userId]?.takeIf { it.deletedAt == null }?.let {
                SiteUserResponse(it.id, it.displayName, it.email, assignment.siteAdmin, assignment.siteOwner)
            }
        }.sortedBy { it.name }
    }

    fun assignUsers(user: AppPrincipal, siteId: UUID, input: List<SiteAssignmentInput>): List<SiteUserResponse> {
        ensure(organization.site(user.tenantId, siteId) != null) { "Site introuvable." }
        val ids = input.mapNotNull { it.userId }.toSet()
        ensure(ids.size == input.size) { "Chaque utilisateur ne peut apparaître qu’une fois." }
        val known = users.findAllByTenantIdAndIdIn(user.tenantId, ids).filter { it.deletedAt == null }.map { it.id }.toSet()
        ensure(known.size == ids.size) { "Un des utilisateurs sélectionnés n’existe pas." }
        db.atomic {
            siteUsers.deleteAll(siteUsers.findAllByKeyTenantIdAndKeySiteId(user.tenantId, siteId))
            db.flush()
            input.forEach {
                siteUsers.save(SiteUserEntity(SiteUserKey(user.tenantId, siteId, requireNotNull(it.userId)), it.siteAdmin, it.siteOwner))
            }
            db.audit(user.tenantId, user.id.toString(), "SITE_USERS", siteId.toString())
        }
        return siteUsers(user, siteId)
    }

    fun preferences(tenant: UUID, id: UUID): NotificationPreferences {
        val account = account(tenant, id)
        return parsePreferences(account).normalized()
    }

    fun parsePreferences(account: UserEntity): NotificationPreferences =
        runCatching { db.json.readValue(account.notificationPreferences, NotificationPreferences::class.java) }
            .getOrDefault(NotificationPreferences())

    fun updatePreferences(tenant: UUID, id: UUID, input: NotificationPreferencesInput): NotificationPreferences =
        db.atomic {
            val account = account(tenant, id)
            account.notificationPreferences = db.encode(input.toPreferences())
            users.save(account)
            parsePreferences(account).normalized()
        }

    fun updateProfile(user: AppPrincipal, input: ProfileInput): UserResponse {
        validateUser(user.email, input.name, input.phone, input.locale)
        db.atomic {
            val account = account(user.tenantId, user.id)
            account.displayName = input.name.trim()
            account.phone = input.phone?.trim()?.ifBlank { null }
            input.locale?.let { account.locale = it }
            users.save(account)
            db.audit(user.tenantId, user.id.toString(), "PROFILE_UPDATE", user.id.toString())
        }
        return find(user, user.id)
    }

    private fun validateUser(email: String, name: String, phone: String?, locale: String?) {
        validateEmail(email)
        ensure(name.isNotBlank() && name.length <= 200) { "Le nom doit contenir entre 1 et 200 caractères." }
        ensure(phone == null || phone.isBlank() || phone.matches(Regex("[+0-9 ().-]{4,40}"))) { "Numéro de téléphone invalide." }
        ensure(locale == null || locale in LOCALES) { "Langue non prise en charge." }
    }

    private companion object {
        val LOCALES = setOf("fr", "en", "de", "es", "it", "pt", "cs")
    }
}
