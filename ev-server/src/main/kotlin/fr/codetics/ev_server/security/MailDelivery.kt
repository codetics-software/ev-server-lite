package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.persistence.AccessTokenRepository
import fr.codetics.ev_server.persistence.AuthAttemptRepository
import fr.codetics.ev_server.persistence.AuthChallengeRepository
import fr.codetics.ev_server.persistence.MailRepository
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MailDelivery(
    private val db: Database,
    private val mail: JavaMailSender,
    private val outbox: MailRepository,
    private val challenges: AuthChallengeRepository,
    private val tokens: AccessTokenRepository,
    private val attempts: AuthAttemptRepository,
    @param:Value("\${app.mail-enabled}") private val enabled: Boolean,
    @param:Value("\${app.mail-from}") private val from: String
) {
    @Scheduled(fixedDelay = 30000)
    fun deliver() {
        db.atomic {
            if (enabled) {
                outbox.findAllBySentAtIsNullAndAttemptsLessThanOrderByCreatedAt(5, PageRequest.of(0, 10))
                    .forEach { message ->
                        try {
                            val email = SimpleMailMessage()
                            email.setFrom(from)
                            email.setTo(message.recipient)
                            email.subject = message.subject
                            email.text = message.body
                            mail.send(email)

                            message.sentAt = Instant.now()
                            message.body = ""
                        } catch (_: Exception) {
                            message.attempts += 1
                        }
                        outbox.save(message)
                    }
            }

            challenges.deleteAllByExpiresAtBefore(Instant.now())
            tokens.deleteAllByExpiresAtBefore(Instant.now())
            attempts.deleteAllByWindowStartBefore(Instant.now().minusSeconds(86400))
            true
        }
    }
}
