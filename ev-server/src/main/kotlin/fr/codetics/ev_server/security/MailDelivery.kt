package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.entities.MailEntity
import fr.codetics.ev_server.repositories.AccessTokenRepository
import fr.codetics.ev_server.repositories.AuthAttemptRepository
import fr.codetics.ev_server.repositories.AuthChallengeRepository
import fr.codetics.ev_server.repositories.MailRepository
import java.time.Duration
import java.time.Instant
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
    private val properties: AppProperties,
) {
    @Scheduled(fixedDelay = 30000)
    fun deliver() {
        db.atomic {
            if (properties.mailEnabled) {
                outbox
                    .findAllBySentAtIsNullAndAttemptsLessThanOrderByCreatedAt(
                        5,
                        PageRequest.of(0, 10),
                    )
                    .mapNotNull { db.lock<MailEntity>(it.id) }
                    .filter { it.sentAt == null }
                    .forEach { message ->
                        try {
                            val email = SimpleMailMessage()
                            email.setFrom(properties.mailFrom)
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
            attempts.deleteAllByWindowStartBefore(Instant.now().minus(Duration.ofDays(1)))
        }
    }
}
