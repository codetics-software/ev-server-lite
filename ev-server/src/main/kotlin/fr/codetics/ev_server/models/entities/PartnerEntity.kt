package fr.codetics.ev_server.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "ocpi_partners")
@Document("ocpi_partners")
class PartnerEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var name: String = "",
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 2) var countryCode: String = "",
    var partyId: String = "",
    var localRole: String = "cpo",
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) var tokenHash: String = "",
    var localTokenEncrypted: String? = null,
    var remoteTokenEncrypted: String? = null,
    var remoteUrl: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) var remoteEndpoints: String = "[]",
    var registered: Boolean = false,
    var enabled: Boolean = true,
)
