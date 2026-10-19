package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "ocpi_partners")
class PartnerEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var name: String = "",
    var countryCode: String = "",
    var partyId: String = "",
    var localRole: String = "cpo",
    var tokenHash: String = "",
    var localTokenEncrypted: String? = null,
    var remoteTokenEncrypted: String? = null,
    var remoteUrl: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) var remoteEndpoints: String = "[]",
    var registered: Boolean = false,
    var enabled: Boolean = true,
)
