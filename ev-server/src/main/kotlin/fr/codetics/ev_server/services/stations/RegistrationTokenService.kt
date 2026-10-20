package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.entities.RegistrationTokenEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.repositories.RegistrationTokenRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.services.organization.Organization
import java.time.Instant
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class RegistrationTokenInput(
    val description: String,
    val siteAreaId: UUID? = null,
    val expiresAt: Instant? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class RegistrationTokenResponse(
    val id: UUID,
    val description: String,
    val siteAreaId: UUID?,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
    val createdAt: Instant,
    val ocppUrl: String,
    val active: Boolean,
    val token: String? = null,
)

data class StationRegistered(val station: StationEntity)

@Service
class RegistrationTokenService(
    private val db: Database,
    private val registrationTokens: RegistrationTokenRepository,
    private val stations: StationRepository,
    private val stationEvents: StationEventRepository,
    private val tenants: TenantRepository,
    private val organization: Organization,
    private val passwords: PasswordEncoder,
    private val properties: AppProperties,
    private val events: ApplicationEventPublisher,
) {
    fun list(user: AppPrincipal): List<RegistrationTokenResponse> {
        val slug = tenants.findById(user.tenantId).orElseThrow().slug
        return registrationTokens.findAllByTenantIdOrderByCreatedAtDesc(user.tenantId).map {
            response(it, slug)
        }
    }

    fun create(user: AppPrincipal, input: RegistrationTokenInput): RegistrationTokenResponse {
        ensure(input.description.isNotBlank() && input.description.length <= 200) {
            "La description doit contenir entre 1 et 200 caractères."
        }
        ensure(input.expiresAt == null || input.expiresAt.isAfter(Instant.now())) {
            "La date d’expiration doit être future."
        }
        input.siteAreaId?.let { area ->
            ensure(organization.siteArea(user.tenantId, area) != null) {
                "La zone sélectionnée n’existe pas dans cet espace."
            }
        }
        val raw = randomToken()
        return db.atomic {
            val token =
                registrationTokens.save(
                    RegistrationTokenEntity(
                        tenantId = user.tenantId,
                        tokenHash = sha256(raw),
                        description = input.description.trim(),
                        siteAreaId = input.siteAreaId,
                        expiresAt = input.expiresAt,
                    )
                )
            db.audit(user.tenantId, user.id.toString(), "REGISTRATION_TOKEN_CREATE", token.id.toString())
            response(token, tenants.findById(user.tenantId).orElseThrow().slug).copy(token = raw)
        }
    }

    fun revoke(user: AppPrincipal, id: UUID): MutationResponse =
        db.atomic {
            val token = registrationTokens.findByTenantIdAndId(user.tenantId, id) ?: throw NotFound()
            if (token.revokedAt == null) token.revokedAt = Instant.now()
            registrationTokens.save(token)
            db.audit(user.tenantId, user.id.toString(), "REGISTRATION_TOKEN_REVOKE", id.toString())
            MutationResponse()
        }

    fun delete(user: AppPrincipal, id: UUID): MutationResponse =
        db.atomic {
            val token = registrationTokens.findByTenantIdAndId(user.tenantId, id) ?: throw NotFound()
            registrationTokens.delete(token)
            db.audit(user.tenantId, user.id.toString(), "REGISTRATION_TOKEN_DELETE", id.toString())
            MutationResponse()
        }

    fun register(tenant: UUID, stationId: String, secret: String): StationEntity? {
        if (secret.length !in 40..100) return null
        val token = registrationTokens.findByTenantIdAndTokenHash(tenant, sha256(secret)) ?: return null
        if (token.revokedAt != null || token.expiresAt?.isBefore(Instant.now()) == true) return null

        return db.atomic {
            val key = StationKey(tenant, stationId)
            val existing = stations.findById(key).orElse(null)
            if (existing != null && !existing.deleted) return@atomic null
            val station =
                (existing ?: StationEntity(key, stationId, "")).apply {
                    name = existing?.name ?: stationId
                    secretHash = requireNotNull(passwords.encode(secret))
                    siteAreaId = token.siteAreaId
                    enabled = true
                    deleted = false
                }
            stations.save(station).also { db.flush() }
            stationEvents.save(
                StationEventEntity(
                    tenantId = tenant,
                    stationId = stationId,
                    type = "REGISTRATION",
                    info = token.description,
                )
            )
            db.audit(tenant, "registration:${token.id}", "STATION_REGISTER", stationId)
            events.publishEvent(StationRegistered(station))
            station
        }
    }

    private fun response(token: RegistrationTokenEntity, slug: String) =
        RegistrationTokenResponse(
            token.id,
            token.description,
            token.siteAreaId,
            token.expiresAt,
            token.revokedAt,
            token.createdAt,
            "${ocppBase()}/ocpp/$slug/{identifiant-borne}",
            token.revokedAt == null && token.expiresAt?.isBefore(Instant.now()) != true,
        )

    private fun ocppBase() =
        properties.ocpi.publicUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
}
