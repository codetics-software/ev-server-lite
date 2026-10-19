package fr.codetics.ev_server.persistence

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

@Embeddable
data class OAuthIdentityKey(
    var issuer: String = "",
    var subject: String = ""
) : Serializable

@Entity
@Table(name = "oauth_identities")
class OAuthIdentityEntity(
    @EmbeddedId var key: OAuthIdentityKey = OAuthIdentityKey(),
    var userId: UUID = UUID.randomUUID()
)
