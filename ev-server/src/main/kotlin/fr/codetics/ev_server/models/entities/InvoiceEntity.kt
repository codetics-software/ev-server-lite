package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "invoices")
@Document("invoices")
class InvoiceEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var stripeAccountId: String = "",
    var stripeInvoiceId: String? = null,
    var number: String? = null,
    var status: String = "DRAFT",
    var amountMinor: Long = 0,
    var currency: String = "EUR",
    var hostedUrl: String? = null,
    var pdfUrl: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) var sessionIds: String = "[]",
    var periodKey: String? = null,
    var lastError: String? = null,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    @Version var revision: Long = 0,
)
