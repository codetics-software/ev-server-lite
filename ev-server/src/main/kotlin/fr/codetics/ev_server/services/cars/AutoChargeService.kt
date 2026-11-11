package fr.codetics.ev_server.services.cars

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.invalid
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.organization.CarData
import fr.codetics.ev_server.ocpp.VehicleIds
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class AutoChargeInput(val transactionId: Int? = null, val vid: String? = null)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class VehicleIdResponse(
    val uid: String,
    val vehicleId: UUID,
    val userId: UUID?,
    val enabled: Boolean,
    val lastUsedAt: Instant?,
) {
    companion object {
        fun from(token: ChargingTokenEntity) =
            VehicleIdResponse(token.key.uid, requireNotNull(token.vehicleId), token.userId, token.enabled, token.lastUsedAt)
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AutoChargeCandidate(
    val transactionId: Int,
    val stationId: String,
    val connectorId: Int,
    val startedAt: Instant,
    val vid: String,
    val vehicleId: UUID?,
)

@Service
class AutoChargeService(
    private val db: Database,
    private val organization: Organization,
    private val tokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
) {
    fun list(user: AppPrincipal, carId: UUID, own: Boolean): List<VehicleIdResponse> {
        car(user, carId, own)
        return vehicleIds(user.tenantId, carId).map(VehicleIdResponse::from)
    }

    fun candidates(user: AppPrincipal): List<AutoChargeCandidate> =
        transactions
            .findAllByTenantIdAndUserIdAndVidIsNotNullAndStartedAtAfter(
                user.tenantId,
                user.id,
                Instant.now().minus(VehicleIds.ENROLLMENT_WINDOW),
            )
            .sortedByDescending { it.startedAt }
            .distinctBy { it.vid }
            .filter { !tokens.existsById(ChargingTokenKey(user.tenantId, requireNotNull(it.vid))) }
            .map {
                AutoChargeCandidate(
                    requireNotNull(it.id),
                    it.stationId,
                    it.connectorId,
                    it.startedAt,
                    requireNotNull(it.vid),
                    it.vehicleId,
                )
            }

    fun enroll(user: AppPrincipal, carId: UUID, input: AutoChargeInput, own: Boolean): VehicleIdResponse {
        val car = car(user, carId, own)
        val owner =
            car.data.userId ?: invalid(msg("autocharge.assign_user_first"))
        ensure((input.transactionId == null) != (input.vid == null)) {
            msg("autocharge.session_or_id")
        }
        val uid =
            if (input.transactionId != null) observed(user, owner, input.transactionId)
            else {
                if (own) throw ResponseStatusException(HttpStatus.FORBIDDEN)
                VehicleIds.normalize(requireNotNull(input.vid))
                    ?: invalid(msg("autocharge.vid_format"))
            }

        return db.atomic {
            val key = ChargingTokenKey(user.tenantId, uid)
            tokens.findById(key).orElse(null)?.let { existing ->
                if (existing.vehicleId == carId && existing.userId == owner) {
                    return@atomic VehicleIdResponse.from(existing)
                }
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    msg("autocharge.already_registered"),
                )
            }
            ensure(vehicleIds(user.tenantId, carId).size < VehicleIds.MAXIMUM_PER_VEHICLE) {
                msg("autocharge.too_many", VehicleIds.MAXIMUM_PER_VEHICLE)
            }
            val token =
                tokens.save(
                    ChargingTokenEntity(
                        key = key,
                        userId = owner,
                        type = VehicleIds.TOKEN_TYPE,
                        description = car.name.take(200),
                        vehicleId = carId,
                    )
                )
            db.audit(user.tenantId, user.id.toString(), "AUTOCHARGE_ENROLL", "$carId/$uid")
            VehicleIdResponse.from(token)
        }
    }

    fun revoke(user: AppPrincipal, carId: UUID, uid: String, own: Boolean): MutationResponse {
        car(user, carId, own)
        val normalized = VehicleIds.normalize(uid) ?: throw NotFound(msg("autocharge.id_not_found"))
        return db.atomic {
            val token =
                tokens.findById(ChargingTokenKey(user.tenantId, normalized)).orElse(null)?.takeIf { it.vehicleId == carId }
                    ?: throw NotFound(msg("autocharge.id_not_found"))
            tokens.delete(token)
            db.audit(user.tenantId, user.id.toString(), "AUTOCHARGE_REVOKE", "$carId/$normalized")
            MutationResponse()
        }
    }

    private fun observed(user: AppPrincipal, owner: UUID, transactionId: Int): String {
        val transaction =
            transactions.findByTenantIdAndId(user.tenantId, transactionId)?.takeIf { it.userId == owner }
                ?: throw NotFound(msg("sessions.not_found"))
        val vid = transaction.vid ?: invalid(msg("autocharge.no_id_in_session"))
        ensure(transaction.startedAt.isAfter(Instant.now().minus(VehicleIds.ENROLLMENT_WINDOW))) {
            msg("autocharge.session_too_old")
        }
        return vid
    }

    private fun vehicleIds(tenant: UUID, carId: UUID) =
        tokens.findAllByKeyTenantIdAndVehicleId(tenant, carId).filter { it.type == VehicleIds.TOKEN_TYPE }

    private fun car(user: AppPrincipal, id: UUID, own: Boolean): Entry<CarData> {
        val car = organization.find(user.tenantId, "cars", id, CarData::class.java)
        if (car == null || (own && car.data.userId != user.id)) throw NotFound(msg("cars.not_found"))
        return car
    }
}
