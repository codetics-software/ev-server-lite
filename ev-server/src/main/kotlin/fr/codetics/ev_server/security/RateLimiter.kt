package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.AuthAttemptEntity
import fr.codetics.ev_server.repositories.AuthAttemptRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class RateLimiter(
    private val attempts: AuthAttemptRepository,
    private val db: Database,
    private val clock: Clock,
) {
    fun check(subject: String, maximum: Int = 10) {
        val hash = sha256(subject)

        repeat(3) {
            try {
                val count =
                    db.isolated {
                        val now = Instant.now(clock)
                        val counter = db.lock<AuthAttemptEntity>(hash)

                        if (counter == null) {
                            db.insert(AuthAttemptEntity(subjectHash = hash, windowStart = now, attempts = 1))
                            return@isolated 1
                        }

                        if (counter.windowStart.isBefore(now.minus(WINDOW))) {
                            counter.windowStart = now
                            counter.attempts = 0
                        }

                        counter.attempts += 1
                        attempts.save(counter)
                        db.flush()
                        counter.attempts
                    }

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

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(15)
    }

    private fun reject(): Nothing {
        throw ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Trop de tentatives. Réessayez plus tard.",
        )
    }
}
