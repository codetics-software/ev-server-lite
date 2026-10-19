package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_users")
class UserEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var email: String = "",
    var displayName: String = "",
    var passwordHash: String? = null,
    var role: String = "DRIVER",
    var enabled: Boolean = true,
    var emailVerified: Boolean = false,
    var eulaVersion: String? = null,
    var createdAt: Instant = Instant.now(),
)
