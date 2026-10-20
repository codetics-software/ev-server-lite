package fr.codetics.ev_server.models.entities

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Embeddable
data class SiteUserKey(
    var tenantId: UUID = UUID.randomUUID(),
    var siteId: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
) : Serializable

@Entity
@Table(name = "site_users")
@Document("site_users")
class SiteUserEntity(
    @EmbeddedId var key: SiteUserKey = SiteUserKey(),
    var siteAdmin: Boolean = false,
    var siteOwner: Boolean = false,
)
