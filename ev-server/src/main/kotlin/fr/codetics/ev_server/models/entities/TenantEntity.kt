package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "tenants")
@Document("tenants")
class TenantEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var slug: String = "",
    var name: String = "",
    var active: Boolean = true,
    var createdAt: Instant = Instant.now(),
    @JdbcTypeCode(SqlTypes.JSON) var components: String = "{}",
    var hasLogo: Boolean = false,
)
