package fr.codetics.ev_server.models.entities

import fr.codetics.ev_server.models.people.ChallengeKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "auth_challenges")
@Document("auth_challenges")
class AuthChallengeEntity(
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) var tokenHash: String = "",
    var userId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) var kind: ChallengeKind = ChallengeKind.VERIFY,
    var expiresAt: Instant = Instant.now(),
)
