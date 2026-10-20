package fr.codetics.ev_server.services.cars

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.entities.IntegrationSecretEntity
import fr.codetics.ev_server.models.organization.CarConnectorData
import fr.codetics.ev_server.models.organization.CarData
import fr.codetics.ev_server.ocpp.TransactionStarted
import fr.codetics.ev_server.repositories.IntegrationSecretRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.security.SecretVault
import fr.codetics.ev_server.services.assets.AssetConnectionError
import fr.codetics.ev_server.services.assets.AssetHttp
import fr.codetics.ev_server.services.assets.ConnectionCheck
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import java.net.http.HttpRequest
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class CarConnectorSecretInput(val clientSecret: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SocResponse(val carId: UUID, val soc: Int?)

@Service
class CarConnectorService(
    private val db: Database,
    private val organization: Organization,
    private val secrets: IntegrationSecretRepository,
    private val transactions: TransactionRepository,
    private val vault: SecretVault,
    private val http: AssetHttp,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun connector(tenant: UUID, id: UUID): Entry<CarConnectorData> =
        organization.find(tenant, "car-connectors", id, CarConnectorData::class.java) ?: throw NotFound("Connecteur introuvable.")

    fun updateSecret(user: AppPrincipal, id: UUID, input: CarConnectorSecretInput): ConnectionCheck {
        connector(user.tenantId, id)
        ensure(input.clientSecret.isNotBlank() && input.clientSecret.length <= 1000) { "Le secret client est invalide." }
        db.atomic {
            val entity = secrets.findById(id).orElse(null)?.takeIf { it.tenantId == user.tenantId } ?: IntegrationSecretEntity(id, user.tenantId)
            entity.secretsEncrypted = vault.encrypt(input.clientSecret)
            entity.updatedAt = Instant.now()
            secrets.save(entity)
            db.audit(user.tenantId, user.id.toString(), "CAR_CONNECTOR_SECRET", id.toString())
        }
        http.forget("car:$id")
        return ConnectionCheck(true, "Secret enregistré.")
    }

    fun check(user: AppPrincipal, id: UUID): ConnectionCheck =
        try {
            token(connector(user.tenantId, id))
            ConnectionCheck(true, "Connexion réussie.")
        } catch (error: AssetConnectionError) {
            ConnectionCheck(false, error.message ?: "Connexion impossible.")
        } catch (error: InvalidRequest) {
            ConnectionCheck(false, error.message)
        }

    fun soc(user: AppPrincipal, carId: UUID): SocResponse {
        val car = organization.find(user.tenantId, "cars", carId, CarData::class.java) ?: throw NotFound("Véhicule introuvable.")
        return SocResponse(carId, soc(user.tenantId, car.data))
    }

    fun soc(tenant: UUID, car: CarData): Int? {
        val connector = car.carConnectorId?.let { connector(tenant, it) } ?: throw InvalidRequest("Aucun connecteur n’est associé à ce véhicule.")
        val token = token(connector)
        val data = connector.data
        val url =
            when (data.type) {
                "TRONITY" -> "/v1/vehicles/${car.carConnectorMeterId ?: throw InvalidRequest("Renseignez l’identifiant Tronity du véhicule.")}/battery"
                else -> "/v1/digital-twin/vehicles/${car.vin ?: throw InvalidRequest("Renseignez le VIN du véhicule.")}"
            }
        val response = http.send(HttpRequest.newBuilder(http.uri(data.apiUrl, url)).header("Authorization", "Bearer $token").GET())
        val value =
            if (data.type == "TRONITY") response.path("level")
            else response.path("vehicleDiagnostics").path("tractionBattery").path("batteryPercentage").path("value")
        return value.takeIf { it.isNumber }?.intValue()?.takeIf { it in 0..100 }
    }

    private fun token(connector: Entry<CarConnectorData>): String {
        val secret =
            secrets.findById(connector.id).orElse(null)?.let { vault.decrypt(it.secretsEncrypted) }
                ?: throw InvalidRequest("Renseignez le secret client du connecteur.")
        val data = connector.data
        return http.token("car:${connector.id}") {
            val response =
                if (data.type == "TRONITY") {
                    http.send(
                        HttpRequest.newBuilder(http.uri(data.apiUrl, "/oauth/authentication"))
                            .header("Content-Type", "application/json")
                            .POST(
                                HttpRequest.BodyPublishers.ofString(
                                    db.encode(mapOf("grant_type" to "app", "client_id" to data.clientId, "client_secret" to secret))
                                )
                            )
                    )
                } else {
                    http.send(
                        HttpRequest.newBuilder(
                                http.uri(requireNotNull(data.authenticationUrl), "/auth/realms/platform-api/protocol/openid-connect/token")
                            )
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(
                                HttpRequest.BodyPublishers.ofString(
                                    http.form(mapOf("grant_type" to "client_credentials", "client_id" to data.clientId, "client_secret" to secret))
                                )
                            )
                    )
                }
            (response.path("access_token").asText().ifBlank { throw AssetConnectionError("Identifiants du connecteur refusés.") }) to
                response.path("expires_in").asLong(600)
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun started(event: TransactionStarted) {
        val transaction = event.transaction
        val vehicle = transaction.vehicleId ?: return
        try {
            val car = organization.find(transaction.tenantId, "cars", vehicle, CarData::class.java) ?: return
            if (car.data.carConnectorId == null) return
            val soc = soc(transaction.tenantId, car.data) ?: return
            db.isolated {
                val stored = transactions.findById(requireNotNull(transaction.id)).orElse(null) ?: return@isolated
                if (stored.socStart == null) stored.socStart = soc
                if (stored.socLast == null) stored.socLast = soc
                transactions.save(stored)
            }
        } catch (error: RuntimeException) {
            log.info("État de charge du véhicule indisponible : {}", error.message)
        }
    }
}
