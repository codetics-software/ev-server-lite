package fr.codetics.ev_server.models.entities

import fr.codetics.ev_server.models.people.UserRole
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "app_users")
@Document("app_users")
class UserEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var email: String = "",
    var displayName: String = "",
    var passwordHash: String? = null,
    @Enumerated(EnumType.STRING) var role: UserRole = UserRole.DRIVER,
    var enabled: Boolean = true,
    var emailVerified: Boolean = false,
    var eulaVersion: String? = null,
    var createdAt: Instant = Instant.now(),
)
