package fr.codetics.ev_server.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "auth_attempts")
@Document("auth_attempts")
class AuthAttemptEntity(
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) var subjectHash: String = "",
    var windowStart: Instant = Instant.now(),
    var attempts: Int = 0,
)
