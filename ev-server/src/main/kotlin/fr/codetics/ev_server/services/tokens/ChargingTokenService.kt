package fr.codetics.ev_server.services.tokens

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Csv
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ImportError
import fr.codetics.ev_server.core.ImportResult
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.tokens.ChargingTokenResponse
import fr.codetics.ev_server.models.tokens.TokenInput
import fr.codetics.ev_server.persistence.Condition
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.anyOf
import fr.codetics.ev_server.persistence.contains
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.persistence.page
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import java.util.UUID
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ChargingTokenService(
    private val db: Database,
    private val tokens: ChargingTokenRepository,
    private val users: UserRepository,
    private val transactions: TransactionRepository,
    private val queries: Queries,
) {
    fun list(
        user: AppPrincipal,
        offset: Int = 0,
        limit: Int = 500,
        search: String = "",
        userId: UUID? = null,
    ): PageResponse<ChargingTokenResponse> {
        val page =
            queries.page<ChargingTokenEntity>(
                conditions(user.tenantId, search, userId),
                pageRequest(offset, limit, Sort.by("key.uid")),
            )
        return PageResponse(views(user.tenantId, page.content), page.totalElements)
    }

    private fun conditions(tenant: UUID, search: String, userId: UUID?): List<Condition> {
        ensure(search.length <= 200) { "La recherche est trop longue." }
        val conditions = mutableListOf<Condition>(eq("key.tenantId", tenant))
        if (search.isNotBlank()) {
            conditions +=
                anyOf(
                    contains("key.uid", search.trim()),
                    contains("visualId", search.trim()),
                    contains("description", search.trim()),
                )
        }
        userId?.let { conditions += eq("userId", it) }
        return conditions
    }

    fun owned(user: AppPrincipal): List<ChargingTokenResponse> =
        views(user.tenantId, tokens.findAllByKeyTenantIdAndUserId(user.tenantId, user.id))

    private fun views(tenant: UUID, page: List<ChargingTokenEntity>): List<ChargingTokenResponse> {
        val names =
            users.findAllByTenantIdAndIdIn(tenant, page.mapNotNull { it.userId }.toSet()).associate {
                it.id to it.displayName
            }
        return page.map { ChargingTokenResponse.from(it, it.userId?.let(names::get)) }
    }

    fun save(body: TokenInput, user: AppPrincipal): MutationResponse {
        validate(body)
        body.userId?.let { requireUser(user.tenantId, it) }

        return db.atomic {
            val key = ChargingTokenKey(user.tenantId, body.uid)
            val token = tokens.findById(key).orElse(null) ?: ChargingTokenEntity(key)
            token.userId = body.userId
            token.enabled = body.enabled
            token.expiresAt = body.expiresAt
            token.visualId = body.visualId?.trim()?.ifBlank { null }
            token.description = body.description?.trim()?.ifBlank { null }
            token.defaultToken = body.default && body.userId != null
            if (token.defaultToken) clearDefault(user.tenantId, requireNotNull(body.userId), body.uid)
            tokens.save(token)
            db.audit(user.tenantId, user.id.toString(), "TOKEN_SAVE", body.uid)
            MutationResponse()
        }
    }

    fun delete(user: AppPrincipal, uid: String): MutationResponse =
        db.atomic {
            val token = find(user.tenantId, uid)
            if (transactions.findAllByTenantIdAndStoppedAtIsNull(user.tenantId).any { it.tokenUid == uid }) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Une session est en cours avec ce badge.")
            }
            tokens.delete(token)
            db.audit(user.tenantId, user.id.toString(), "TOKEN_DELETE", uid)
            MutationResponse()
        }

    fun assign(user: AppPrincipal, uid: String, userId: UUID): MutationResponse {
        requireUser(user.tenantId, userId)
        return db.atomic {
            val token = find(user.tenantId, uid)
            ensure(token.userId == null || token.userId == userId) { "Ce badge est déjà attribué à un autre utilisateur." }
            token.userId = userId
            tokens.save(token)
            db.audit(user.tenantId, user.id.toString(), "TOKEN_ASSIGN", "$uid/$userId")
            MutationResponse()
        }
    }

    fun unassign(user: AppPrincipal, uid: String): MutationResponse =
        db.atomic {
            val token = find(user.tenantId, uid)
            token.userId = null
            token.defaultToken = false
            tokens.save(token)
            db.audit(user.tenantId, user.id.toString(), "TOKEN_UNASSIGN", uid)
            MutationResponse()
        }

    fun export(user: AppPrincipal) =
        Csv.download(
            "badges",
            listOf("uid", "visualId", "description", "userEmail", "actif", "expiration", "derniere_utilisation"),
            queries.list<ChargingTokenEntity>(conditions(user.tenantId, "", null), Sort.by("key.uid")).let { all ->
                val emails =
                    users.findAllByTenantIdAndIdIn(user.tenantId, all.mapNotNull { it.userId }.toSet()).associate { it.id to it.email }
                all.asSequence().map { token ->
                    listOf(
                        token.key.uid,
                        token.visualId,
                        token.description,
                        token.userId?.let(emails::get),
                        if (token.enabled) "oui" else "non",
                        token.expiresAt,
                        token.lastUsedAt,
                    )
                }
            },
        )

    fun import(user: AppPrincipal, content: String): ImportResult {
        val rows = Csv.read(content, 10000)
        var created = 0
        var updated = 0
        val errors = mutableListOf<ImportError>()
        rows.forEachIndexed { index, row ->
            try {
                val uid = row["uid"].orEmpty()
                val email = (row["useremail"] ?: row["email"]).orEmpty().lowercase()
                val owner =
                    if (email.isBlank()) null
                    else users.findByTenantIdAndEmail(user.tenantId, email)?.takeIf { it.deletedAt == null }
                        ?: throw InvalidRequest("Utilisateur inconnu : $email.")
                val input =
                    TokenInput(
                        uid = uid,
                        userId = owner?.id,
                        visualId = row["visualid"],
                        description = row["description"],
                    )
                validate(input)
                db.atomic {
                    val key = ChargingTokenKey(user.tenantId, uid)
                    val existing = tokens.findById(key).orElse(null)
                    val token = existing ?: ChargingTokenEntity(key)
                    if (owner != null) token.userId = owner.id
                    input.visualId?.ifBlank { null }?.let { token.visualId = it }
                    input.description?.ifBlank { null }?.let { token.description = it }
                    tokens.save(token)
                    if (existing == null) created += 1 else updated += 1
                }
            } catch (error: IllegalArgumentException) {
                errors += ImportError(index + 2, error.message ?: "Ligne invalide.")
            }
        }
        db.audit(user.tenantId, user.id.toString(), "TOKENS_IMPORT", "${created + updated}")
        return ImportResult(created = created, updated = updated, errors = errors)
    }

    private fun clearDefault(tenant: UUID, userId: UUID, keep: String) {
        tokens.findAllByKeyTenantIdAndUserId(tenant, userId).filter { it.defaultToken && it.key.uid != keep }.forEach {
            it.defaultToken = false
            tokens.save(it)
        }
    }

    private fun find(tenant: UUID, uid: String) =
        tokens.findById(ChargingTokenKey(tenant, uid)).orElse(null) ?: throw NotFound("Badge introuvable.")

    private fun requireUser(tenant: UUID, id: UUID) {
        if (users.findByTenantIdAndId(tenant, id)?.takeIf { it.deletedAt == null } == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur indisponible.")
        }
    }

    private fun validate(body: TokenInput) {
        ensure(body.uid.matches(Regex("[A-Za-z0-9_-]{1,20}"))) {
            "L’identifiant du badge OCPP 1.6 doit contenir au plus 20 caractères."
        }
        ensure(body.visualId == null || body.visualId.length <= 100) { "L’identifiant visible contient 100 caractères au plus." }
        ensure(body.description == null || body.description.length <= 200) { "La description contient 200 caractères au plus." }
    }
}
