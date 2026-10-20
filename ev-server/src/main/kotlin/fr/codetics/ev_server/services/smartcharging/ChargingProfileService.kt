package fr.codetics.ev_server.services.smartcharging

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.commands.CommandReceipt
import fr.codetics.ev_server.models.entities.ChargingProfileEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.stations.PowerLimitInput
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.repositories.ChargingProfileRepository
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.StationRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class ChargingProfileInput(val stationId: String, val connectorId: Int, val profile: JsonNode)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ChargingProfileResponse(
    val id: UUID,
    val stationId: String,
    val connectorId: Int,
    val profileId: Int,
    val purpose: String,
    val stackLevel: Int,
    val transactionId: Int?,
    val profile: JsonNode,
    val source: String,
    val createdAt: Instant,
    val commandState: String?,
)

@Service
class ChargingProfileService(
    private val db: Database,
    private val profiles: ChargingProfileRepository,
    private val stations: StationRepository,
    private val commands: CommandRepository,
    private val queue: OcppCommandQueue,
) {
    fun list(user: AppPrincipal, stationId: String?): List<ChargingProfileResponse> {
        val rows =
            if (stationId.isNullOrBlank()) profiles.findAllByTenantIdOrderByCreatedAtDesc(user.tenantId)
            else profiles.findAllByTenantIdAndStationIdOrderByCreatedAtDesc(user.tenantId, stationId)
        val states = commands.findAllById(rows.mapNotNull { it.commandId }).associate { it.id to it.state.name }
        return rows.map { response(it, it.commandId?.let(states::get)) }
    }

    fun create(user: AppPrincipal, input: ChargingProfileInput): ChargingProfileResponse {
        requireStation(user.tenantId, input.stationId)
        val profile = validate(input.connectorId, input.profile)
        val entity =
            db.atomic {
                val saved = apply(user.tenantId, input.stationId, input.connectorId, profile, "MANUAL")
                db.audit(user.tenantId, user.id.toString(), "CHARGING_PROFILE_SET", "${input.stationId}/${saved.profileId}")
                saved
            }
        return response(entity, "QUEUED")
    }

    fun delete(user: AppPrincipal, id: UUID): MutationResponse =
        db.atomic {
            val profile = profiles.findByTenantIdAndId(user.tenantId, id) ?: throw NotFound("Profil introuvable.")
            clear(profile)
            db.audit(user.tenantId, user.id.toString(), "CHARGING_PROFILE_CLEAR", "${profile.stationId}/${profile.profileId}")
            MutationResponse()
        }

    fun powerLimit(user: AppPrincipal, stationId: String, input: PowerLimitInput): CommandReceipt {
        val station = requireStation(user.tenantId, stationId)
        ensure(input.connectorId in 0..100) { "Numéro de connecteur invalide." }
        ensure(input.limitKw.signum() >= 0 && input.limitKw <= BigDecimal(10000)) { "La limite doit être positive." }
        station.maximumPowerKw?.let { maximum ->
            ensure(input.limitKw <= maximum) { "La limite dépasse la puissance maximale de la borne." }
        }
        val purpose = if (input.connectorId == 0) "ChargePointMaxProfile" else "TxDefaultProfile"
        val profile =
            db.json.valueToTree<JsonNode>(
                mapOf(
                    "chargingProfileId" to POWER_LIMIT_ID + input.connectorId,
                    "stackLevel" to 8,
                    "chargingProfilePurpose" to purpose,
                    "chargingProfileKind" to "Relative",
                    "chargingSchedule" to
                        mapOf(
                            "chargingRateUnit" to "W",
                            "chargingSchedulePeriod" to
                                listOf(
                                    mapOf(
                                        "startPeriod" to 0,
                                        "limit" to input.limitKw.multiply(BigDecimal(1000)).setScale(1, RoundingMode.HALF_UP),
                                    )
                                ),
                        ),
                )
            )
        val entity =
            db.atomic {
                profiles.findAllByTenantIdAndStationIdAndSource(user.tenantId, stationId, "POWER_LIMIT")
                    .filter { it.connectorId == input.connectorId }
                    .forEach(profiles::delete)
                val saved = apply(user.tenantId, stationId, input.connectorId, profile, "POWER_LIMIT")
                db.audit(user.tenantId, user.id.toString(), "STATION_POWER_LIMIT", "$stationId/${input.connectorId}/${input.limitKw}")
                saved
            }
        return CommandReceipt(requireNotNull(entity.commandId))
    }

    fun apply(tenant: UUID, stationId: String, connectorId: Int, profile: JsonNode, source: String, transactionId: Int? = null): ChargingProfileEntity {
        val profileId = profile.path("chargingProfileId").intValue()
        profiles.findAllByTenantIdAndStationIdOrderByCreatedAtDesc(tenant, stationId)
            .filter { it.profileId == profileId && it.connectorId == connectorId }
            .forEach(profiles::delete)
        val command =
            queue.command(
                tenant,
                stationId,
                "SetChargingProfile",
                db.json.valueToTree(mapOf("connectorId" to connectorId, "csChargingProfiles" to profile)),
            )
        return profiles.save(
            ChargingProfileEntity(
                tenantId = tenant,
                stationId = stationId,
                connectorId = connectorId,
                profileId = profileId,
                purpose = profile.path("chargingProfilePurpose").asText(),
                stackLevel = profile.path("stackLevel").intValue(),
                transactionId = transactionId ?: profile.get("transactionId")?.intValue(),
                profile = db.encode(profile),
                source = source,
                commandId = command,
            )
        )
    }

    fun clear(profile: ChargingProfileEntity) {
        queue.command(
            profile.tenantId,
            profile.stationId,
            "ClearChargingProfile",
            db.json.valueToTree(mapOf("id" to profile.profileId)),
        )
        profiles.delete(profile)
    }

    private fun requireStation(tenant: UUID, stationId: String) =
        stations.findById(StationKey(tenant, stationId)).orElse(null)?.takeUnless { it.deleted }
            ?: throw NotFound("Borne introuvable.")

    private fun validate(connectorId: Int, profile: JsonNode): JsonNode {
        ensure(connectorId in 0..100) { "Numéro de connecteur invalide." }
        ensure(profile.isObject) { "Le profil doit être un objet OCPP csChargingProfiles." }
        val id = profile.path("chargingProfileId")
        ensure(id.isIntegralNumber && id.intValue() in 1..Int.MAX_VALUE) { "chargingProfileId doit être un entier positif." }
        ensure(profile.path("stackLevel").isIntegralNumber && profile.path("stackLevel").intValue() >= 0) {
            "stackLevel doit être un entier positif ou nul."
        }
        val purpose = profile.path("chargingProfilePurpose").asText()
        ensure(purpose in setOf("ChargePointMaxProfile", "TxDefaultProfile", "TxProfile")) { "chargingProfilePurpose inconnu." }
        ensure(purpose != "ChargePointMaxProfile" || connectorId == 0) {
            "Un profil ChargePointMaxProfile s’applique au connecteur 0."
        }
        ensure(purpose != "TxProfile" || profile.path("transactionId").isIntegralNumber) {
            "Un profil TxProfile doit cibler une session."
        }
        ensure(profile.path("chargingProfileKind").asText() in setOf("Absolute", "Recurring", "Relative")) {
            "chargingProfileKind inconnu."
        }
        val schedule = profile.path("chargingSchedule")
        ensure(schedule.path("chargingRateUnit").asText() in setOf("W", "A")) { "L’unité doit être W ou A." }
        val periods = schedule.path("chargingSchedulePeriod")
        ensure(periods.isArray && periods.size() in 1..48) { "Renseignez entre 1 et 48 périodes." }
        var previous = -1
        periods.forEach { period ->
            val start = period.path("startPeriod")
            ensure(start.isIntegralNumber && start.intValue() > previous) { "Les périodes doivent être croissantes." }
            previous = start.intValue()
            ensure(period.path("limit").isNumber && period.path("limit").decimalValue().signum() >= 0) {
                "Chaque période doit avoir une limite positive."
            }
        }
        ensure(previous >= 0 && periods[0].path("startPeriod").intValue() == 0) { "La première période commence à 0." }
        ensure(db.encode(profile).length <= 16384) { "Le profil est trop volumineux." }
        return profile
    }

    private fun response(profile: ChargingProfileEntity, state: String?) =
        ChargingProfileResponse(
            profile.id,
            profile.stationId,
            profile.connectorId,
            profile.profileId,
            profile.purpose,
            profile.stackLevel,
            profile.transactionId,
            db.decode(profile.profile),
            profile.source,
            profile.createdAt,
            state,
        )

    companion object {
        const val POWER_LIMIT_ID = 900
        const val SMART_CHARGING_ID = 1000
    }
}
