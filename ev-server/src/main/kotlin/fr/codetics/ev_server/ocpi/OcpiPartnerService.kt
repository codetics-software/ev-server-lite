package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.OcpiJobEntity
import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.page
import fr.codetics.ev_server.repositories.OcpiJobRepository
import fr.codetics.ev_server.repositories.OcpiObjectRepository
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.security.SecretVault
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class PartnerUpdate(val name: String, val remoteUrl: String, val enabled: Boolean = true)

data class PartnerRegistration(val token: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PartnerResponse(
    val id: UUID,
    val name: String,
    val countryCode: String,
    val partyId: String,
    val localRole: String,
    val remoteUrl: String?,
    val registered: Boolean,
    val enabled: Boolean,
    val status: String,
    val lastPingAt: Instant?,
    val lastPushAt: Instant?,
    val lastPullAt: Instant?,
    val lastError: String?,
    val modules: List<String>,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class JobResponse(
    val id: UUID,
    val module: String,
    val direction: String,
    val total: Int,
    val success: Int,
    val failure: Int,
    val errors: List<String>,
    val startedAt: Instant,
    val finishedAt: Instant?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ObjectResponse(val path: String, val data: JsonNode, val lastUpdated: Instant)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PingResponse(val ok: Boolean, val message: String)

@Service
class OcpiPartnerService(
    private val db: Database,
    private val partners: PartnerRepository,
    private val objects: OcpiObjectRepository,
    private val jobs: OcpiJobRepository,
    private val transport: OcpiTransport,
    private val credentials: OcpiCredentials,
    private val publisher: OcpiPublisher,
    private val mapper: OcpiMapper,
    private val vault: SecretVault,
    private val queries: Queries,
) {
    fun list(user: AppPrincipal): List<PartnerResponse> = partners.findAllByTenantIdOrderByName(user.tenantId).map(::response)

    fun response(partner: PartnerEntity): PartnerResponse {
        val modules =
            runCatching { db.decode(partner.remoteEndpoints).toList().map { it.path("identifier").asText() } }.getOrDefault(emptyList<String>())
        val status =
            when {
                !partner.enabled -> "DISABLED"
                partner.lastError != null && partner.registered -> "ERROR"
                partner.registered -> "REGISTERED"
                else -> "PENDING"
            }
        return PartnerResponse(
            partner.id,
            partner.name,
            partner.countryCode,
            partner.partyId,
            partner.localRole,
            partner.remoteUrl,
            partner.registered,
            partner.enabled,
            status,
            partner.lastPingAt,
            partner.lastPushAt,
            partner.lastPullAt,
            partner.lastError,
            modules,
        )
    }

    private fun partner(user: AppPrincipal, id: UUID) = partners.findByTenantIdAndId(user.tenantId, id) ?: throw NotFound("Partenaire introuvable.")

    fun update(user: AppPrincipal, id: UUID, input: PartnerUpdate): PartnerResponse {
        ensure(input.name.isNotBlank() && input.name.length <= 200) { "Le nom doit contenir entre 1 et 200 caractères." }
        transport.validateRemote(input.remoteUrl, input.remoteUrl)
        return db.atomic {
            val partner = db.lock<PartnerEntity>(id)?.takeIf { it.tenantId == user.tenantId } ?: throw NotFound("Partenaire introuvable.")
            ensure(!partner.registered || partner.remoteUrl == input.remoteUrl) {
                "Désenregistrez le partenaire avant de changer son adresse."
            }
            ensure(partner.localTokenEncrypted != null || !input.enabled) {
                "Ce partenaire a été révoqué : créez-en un nouveau pour rétablir la connexion."
            }
            partner.name = input.name.trim()
            partner.remoteUrl = input.remoteUrl
            partner.enabled = input.enabled
            partners.save(partner)
            db.audit(user.tenantId, user.id.toString(), "OCPI_PARTNER_UPDATE", id.toString())
            response(partner)
        }
    }

    fun register(user: AppPrincipal, id: UUID, input: PartnerRegistration): PartnerResponse {
        val partner = partner(user, id)
        ensure(partner.enabled && partner.localTokenEncrypted != null) { "Ce partenaire est désactivé." }
        ensure(!partner.registered) { "Ce partenaire est déjà enregistré." }
        ensure(input.token.isNotBlank() && input.token.length <= 64) { "Le jeton fourni par le partenaire est invalide." }
        val remote = requireNotNull(partner.remoteUrl)
        val failure =
            try {
                val versions = transport.request(partner, remote, token = input.token)
                val version =
                    versions.firstOrNull { it.path("version").asText() == "2.1.1" }
                        ?: throw OcpiFault(3001, "Le partenaire ne publie pas OCPI 2.1.1.")
                val details = transport.request(partner, version.path("url").asText(), token = input.token)
                val endpoints = details.path("endpoints")
                endpoints.forEach { transport.validateRemote(it.path("url").asText(), remote) }
                val credentialsUrl =
                    endpoints.firstOrNull { it.path("identifier").asText() == "credentials" }?.path("url")?.asText()
                        ?: throw OcpiFault(3001, "Le partenaire ne publie pas le module credentials.")
                val local = vault.decrypt(requireNotNull(partner.localTokenEncrypted))
                val theirs =
                    transport.request(
                        partner,
                        credentialsUrl,
                        "POST",
                        credentials.ourCredentials(partner.localRole, local),
                        input.token,
                    )
                val token = theirs.path("token").asText()
                if (token.isBlank() || token.length > 64) throw OcpiFault(3001, "Le partenaire n’a pas renvoyé de jeton valide.")
                db.atomic {
                    val locked = requireNotNull(db.lock<PartnerEntity>(id))
                    locked.remoteTokenEncrypted = vault.encrypt(token)
                    locked.remoteEndpoints = db.encode(endpoints)
                    locked.registered = true
                    locked.lastError = null
                    locked.lastPingAt = Instant.now()
                    partners.save(locked)
                    db.audit(user.tenantId, user.id.toString(), "OCPI_PARTNER_REGISTER", id.toString())
                }
                null
            } catch (fault: OcpiFault) {
                fault.message
            }
        if (failure != null) {
            markError(id, failure)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, failure)
        }
        publisher.rebuild(user.tenantId)
        return response(partner(user, id))
    }

    fun unregister(user: AppPrincipal, id: UUID): PartnerResponse {
        val partner = partner(user, id)
        ensure(partner.registered) { "Ce partenaire n’est pas enregistré." }
        val url = transport.endpoint(partner, "credentials")
        val failure =
            try {
                if (url != null) transport.request(partner, url, "DELETE")
                null
            } catch (fault: OcpiFault) {
                fault.message
            }
        db.atomic {
            val locked = requireNotNull(db.lock<PartnerEntity>(id))
            locked.registered = false
            locked.remoteTokenEncrypted = null
            locked.remoteEndpoints = "[]"
            locked.lastError = failure
            partners.save(locked)
            db.audit(user.tenantId, user.id.toString(), "OCPI_PARTNER_UNREGISTER", id.toString())
        }
        return response(partner(user, id))
    }

    fun ping(user: AppPrincipal, id: UUID): PingResponse {
        val partner = partner(user, id)
        val result =
            try {
                ensure(partner.registered) { "Enregistrez d’abord le partenaire." }
                val versions = transport.request(partner, requireNotNull(partner.remoteUrl))
                if (versions.none { it.path("version").asText() == "2.1.1" }) throw OcpiFault(3001, "Le partenaire ne publie plus OCPI 2.1.1.")
                PingResponse(true, "Le partenaire répond.")
            } catch (fault: OcpiFault) {
                PingResponse(false, fault.message)
            }
        db.atomic {
            val locked = requireNotNull(db.lock<PartnerEntity>(id))
            locked.lastPingAt = Instant.now()
            locked.lastError = if (result.ok) null else result.message
            partners.save(locked)
        }
        return result
    }

    private fun markError(id: UUID, message: String) {
        db.atomic {
            db.lock<PartnerEntity>(id)?.let {
                it.lastError = message.take(500)
                partners.save(it)
            }
        }
    }

    fun push(user: AppPrincipal, id: UUID, module: String): JobResponse {
        val partner = partner(user, id)
        ensure(partner.registered && partner.enabled) { "Enregistrez d’abord le partenaire." }
        val allowed = if (partner.localRole == "cpo") setOf("locations", "statuses", "tariffs", "sessions", "cdrs") else setOf("tokens")
        ensure(module in allowed) { "Ce module ne peut pas être envoyé à ce partenaire." }
        val identifier = if (module == "statuses") "locations" else module
        val base = transport.endpoint(partner, identifier) ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Le partenaire ne publie pas le module $identifier.")
        val prefix = "/${mapper.countryCode}/${mapper.partyId}"
        val operations = mutableListOf<Triple<String, String, Any?>>()
        when (module) {
            "locations", "statuses", "tariffs" -> {
                val snapshot = publisher.snapshot(user.tenantId)
                if (module == "tariffs") {
                    snapshot.tariffs.forEach { (key, value) -> operations += Triple("PUT", "$base$prefix/$key", value) }
                } else {
                    snapshot.locations.forEach { (key, value) ->
                        if (module == "locations") {
                            operations += Triple("PUT", "$base$prefix/$key", value)
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            (value["evses"] as List<Map<String, Any?>>).forEach { evse ->
                                operations += Triple(
                                    "PATCH",
                                    "$base$prefix/$key/${evse["uid"]}",
                                    mapOf("status" to evse["status"], "last_updated" to evse["last_updated"]),
                                )
                            }
                        }
                    }
                }
            }
            "sessions", "cdrs" -> {
                val snapshot = publisher.snapshot(user.tenantId)
                publisher.sessionsFor(partner).filter { module == "sessions" || it.stoppedAt != null }.forEach { transaction ->
                    val location = publisher.location(transaction, snapshot) ?: return@forEach
                    if (module == "sessions") {
                        operations += Triple("PUT", "$base$prefix/${transaction.id}", mapper.session(transaction, location, "WHITELIST"))
                    } else {
                        val tariff = transaction.pricingDefinitionId?.let { publisher.tariffEntry(user.tenantId, it) }?.let(mapper::tariff)
                        operations += Triple("POST", base, mapper.cdr(transaction, location, "WHITELIST", tariff))
                    }
                }
            }
            "tokens" -> {
                publisher.tokensFor(user.tenantId).forEach { (token, value) ->
                    operations += Triple("PUT", "$base$prefix/${token.key.uid}", value)
                }
            }
        }
        val job = OcpiJobEntity(tenantId = user.tenantId, partnerId = id, module = module, direction = "PUSH", total = operations.size)
        val errors = mutableListOf<String>()
        operations.take(MAXIMUM_OPERATIONS).forEach { (method, url, body) ->
            try {
                transport.request(partner, url, method, body)
                job.success += 1
            } catch (fault: OcpiFault) {
                job.failure += 1
                if (errors.size < 20) errors += "${url.substringAfterLast('/')} : ${fault.message}"
            }
        }
        job.failure += (operations.size - MAXIMUM_OPERATIONS).coerceAtLeast(0)
        return finish(user, partner, job, errors, push = true)
    }

    fun pull(user: AppPrincipal, id: UUID, module: String): JobResponse {
        val partner = partner(user, id)
        ensure(partner.registered && partner.enabled) { "Enregistrez d’abord le partenaire." }
        val allowed = if (partner.localRole == "cpo") setOf("tokens") else setOf("locations", "sessions", "cdrs", "tariffs")
        ensure(module in allowed) { "Ce module ne peut pas être récupéré depuis ce partenaire." }
        var url: String? = transport.endpoint(partner, module) ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Le partenaire ne publie pas le module $module.")
        val job = OcpiJobEntity(tenantId = user.tenantId, partnerId = id, module = module, direction = "PULL")
        val errors = mutableListOf<String>()
        var pages = 0
        while (url != null && pages < MAXIMUM_PAGES) {
            val page =
                try {
                    transport.page(partner, url)
                } catch (fault: OcpiFault) {
                    errors += fault.message
                    break
                }
            pages += 1
            db.atomic {
                page.data.forEach { item ->
                    job.total += 1
                    val key = if (module == "tokens") item.path("uid").asText() else item.path("id").asText()
                    val updated = runCatching { Instant.parse(item.path("last_updated").asText()) }.getOrNull()
                    if (key.isBlank() || key.length > 64 || updated == null) {
                        job.failure += 1
                        return@forEach
                    }
                    val path = if (module == "cdrs") key else "${partner.countryCode}/${partner.partyId}/$key"
                    objects.save(OcpiObjectEntity(OcpiObjectKey(user.tenantId, id, module, "RECEIVER", path), db.encode(item), updated))
                    job.success += 1
                }
            }
            url = page.next?.takeIf { next -> runCatching { transport.validateRemote(next, requireNotNull(partner.remoteUrl)) }.isSuccess }
        }
        return finish(user, partner, job, errors, push = false)
    }

    private fun finish(user: AppPrincipal, partner: PartnerEntity, job: OcpiJobEntity, errors: List<String>, push: Boolean): JobResponse {
        job.errors = db.encode(errors)
        job.finishedAt = Instant.now()
        db.atomic {
            jobs.save(job)
            val locked = requireNotNull(db.lock<PartnerEntity>(partner.id))
            if (push) locked.lastPushAt = Instant.now() else locked.lastPullAt = Instant.now()
            locked.lastError = errors.firstOrNull()
            partners.save(locked)
            db.audit(user.tenantId, user.id.toString(), "OCPI_${job.direction}_${job.module.uppercase()}", partner.id.toString())
        }
        return job(job)
    }

    fun jobs(user: AppPrincipal, id: UUID): List<JobResponse> {
        partner(user, id)
        return jobs.findAllByTenantIdAndPartnerIdOrderByStartedAtDesc(user.tenantId, id, PageRequest.of(0, 50)).map(::job)
    }

    fun objects(user: AppPrincipal, id: UUID, module: String, direction: String, offset: Int, limit: Int): PageResponse<ObjectResponse> {
        partner(user, id)
        ensure(direction in setOf("SENDER", "RECEIVER")) { "Sens inconnu." }
        ensure(module in setOf("locations", "sessions", "cdrs", "tariffs", "tokens")) { "Module inconnu." }
        val page =
            queries.page<OcpiObjectEntity>(
                listOf(
                    eq("key.tenantId", user.tenantId),
                    eq("key.partnerId", id),
                    eq("key.module", module),
                    eq("key.direction", direction),
                ),
                pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "lastUpdated")),
            )
        return PageResponse(page.content.map { ObjectResponse(it.key.objectPath, db.decode(it.data), it.lastUpdated) }, page.totalElements)
    }

    private fun job(entity: OcpiJobEntity) =
        JobResponse(
            entity.id,
            entity.module,
            entity.direction,
            entity.total,
            entity.success,
            entity.failure,
            db.decode(entity.errors).toList().map { it.asText() },
            entity.startedAt,
            entity.finishedAt,
        )

    private companion object {
        const val MAXIMUM_OPERATIONS = 2000
        const val MAXIMUM_PAGES = 100
    }
}
