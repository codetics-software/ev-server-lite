package fr.codetics.ev_server.services.people

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.RecordInput
import fr.codetics.ev_server.models.api.RecordResponse
import fr.codetics.ev_server.models.api.SavedRecord
import fr.codetics.ev_server.models.organization.CarData
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.configuration.RecordService
import fr.codetics.ev_server.services.organization.Organization
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class AccountCars(
    private val db: Database,
    private val records: RecordService,
    private val organization: Organization,
    private val users: UserRepository,
) {
    fun list(user: AppPrincipal): List<RecordResponse> =
        organization.cars(user.tenantId).filter { it.data.userId == user.id }.map {
            RecordResponse(it.id, it.name, db.json.valueToTree(it.data), it.revision, it.updatedAt)
        }

    fun create(user: AppPrincipal, input: RecordInput): SavedRecord {
        val saved = records.save("cars", null, owned(user, input), user)
        if (input.data["default"] == true) setDefault(user, saved.id)
        return saved
    }

    fun update(user: AppPrincipal, id: UUID, input: RecordInput): SavedRecord {
        requireOwned(user, id)
        val saved = records.save("cars", id, owned(user, input), user)
        if (input.data["default"] == true) setDefault(user, id)
        return saved
    }

    fun delete(user: AppPrincipal, id: UUID): MutationResponse {
        requireOwned(user, id)
        db.atomic {
            users.findByTenantIdAndId(user.tenantId, user.id)?.takeIf { it.defaultCarId == id }?.let {
                it.defaultCarId = null
                users.save(it)
            }
        }
        return records.remove("cars", id, user)
    }

    private fun owned(user: AppPrincipal, input: RecordInput): RecordInput {
        ensure(input.data["userId"] == null || input.data["userId"] == user.id.toString()) {
            "Un véhicule personnel ne peut pas être attribué à un autre utilisateur."
        }
        return input.copy(data = input.data + ("userId" to user.id.toString()))
    }

    private fun requireOwned(user: AppPrincipal, id: UUID) {
        val car = organization.find(user.tenantId, "cars", id, CarData::class.java)
        if (car == null || car.data.userId != user.id) throw NotFound("Véhicule introuvable.")
    }

    private fun setDefault(user: AppPrincipal, id: UUID) {
        db.atomic {
            users.findByTenantIdAndId(user.tenantId, user.id)?.let {
                it.defaultCarId = id
                users.save(it)
            }
        }
    }
}
