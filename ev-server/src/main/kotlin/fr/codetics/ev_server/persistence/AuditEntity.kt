package fr.codetics.ev_server.persistence

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_events")
class AuditEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var tenantId: UUID = UUID.randomUUID(),
    var actor: String = "",
    var action: String = "",
    var target: String = "",
    var createdAt: Instant = Instant.now()
)
