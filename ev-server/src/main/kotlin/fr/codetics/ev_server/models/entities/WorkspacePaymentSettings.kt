package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "workspace_payment_settings")
@Document("workspace_payment_settings")
class WorkspacePaymentSettings(
    @Id var tenantId: UUID = UUID.randomUUID(),
    var stripeAccountId: String? = null,
    var country: String = "FR",
    var currency: String = "EUR",
    var enabled: Boolean = false,
    var userBillingEnabled: Boolean = false,
    var userBillingMode: String = "PER_SESSION",
    var taxRateId: String? = null,
    var updatedAt: Instant = Instant.now(),
    @Version var revision: Long = 0,
)
