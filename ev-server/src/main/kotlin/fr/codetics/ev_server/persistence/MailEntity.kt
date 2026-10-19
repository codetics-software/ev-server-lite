package fr.codetics.ev_server.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mail_outbox")
class MailEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var recipient: String = "",
    var subject: String = "",
    var body: String = "",
    var attempts: Int = 0,
    var sentAt: Instant? = null,
    var createdAt: Instant = Instant.now()
)
