package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.sha256
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object PortalAccessToken {
    fun validateFormat(token: String) {
        ensure(token.matches(Regex("[a-f0-9]{64}"))) { "Le jeton de suivi est invalide." }
    }

    fun verify(expectedHash: String, token: String) {
        validateFormat(token)
        if (!MessageDigest.isEqual(expectedHash.toByteArray(), sha256(token).toByteArray())) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable.")
        }
    }
}
