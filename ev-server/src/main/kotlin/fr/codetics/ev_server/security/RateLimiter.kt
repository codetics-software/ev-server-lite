package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.AuthAttemptEntity
import fr.codetics.ev_server.repositories.AuthAttemptRepository
import java.time.Instant
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

@Component
class RateLimiter(
    private val attempts: AuthAttemptRepository,
    manager: PlatformTransactionManager,
) {
    private val transactions =
        TransactionTemplate(manager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    fun check(subject: String, maximum: Int = 10) {
        val hash = sha256(subject)

        repeat(3) {
            try {
                val count =
                    transactions.execute {
                        val now = Instant.now()
                        val counter =
                            attempts.findForUpdate(hash)
                                ?: AuthAttemptEntity(subjectHash = hash, windowStart = now)

                        if (counter.windowStart.isBefore(now.minusSeconds(900))) {
                            counter.windowStart = now
                            counter.attempts = 0
                        }

                        counter.attempts += 1
                        attempts.saveAndFlush(counter)
                        counter.attempts
                    } ?: maximum + 1

                if (count > maximum) {
                    reject()
                }

                return
            } catch (_: DataIntegrityViolationException) {
                // Deux premières demandes peuvent créer le même compteur : la clé unique
                // impose une reprise dans une nouvelle transaction avec verrouillage.
            }
        }

        reject()
    }

    private fun reject(): Nothing {
        throw ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Trop de tentatives. Réessayez plus tard.",
        )
    }
}
