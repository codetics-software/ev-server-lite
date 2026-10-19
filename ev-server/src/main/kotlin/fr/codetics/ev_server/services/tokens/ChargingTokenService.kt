package fr.codetics.ev_server.services.tokens

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.tokens.ChargingTokenResponse
import fr.codetics.ev_server.models.tokens.TokenInput
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ChargingTokenService(
    private val db: Database,
    private val tokens: ChargingTokenRepository,
    private val users: UserRepository,
) {
    fun list(user: AppPrincipal): List<ChargingTokenResponse> =
        tokens.findAllByKeyTenantId(user.tenantId, PageRequest.of(0, 500)).content.map { token ->
            ChargingTokenResponse(
                token.key.uid,
                token.userId,
                token.type,
                token.enabled,
                token.expiresAt,
            )
        }

    fun save(body: TokenInput, user: AppPrincipal): MutationResponse {
        ensure(body.uid.matches(Regex("[A-Za-z0-9_-]{1,20}"))) {
            "L’identifiant du badge OCPP 1.6 doit contenir au plus 20 caractères."
        }
        if (body.userId != null && users.findByTenantIdAndId(user.tenantId, body.userId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur indisponible.")
        }

        return db.atomic {
            tokens.save(
                ChargingTokenEntity(
                    key = ChargingTokenKey(user.tenantId, body.uid),
                    userId = body.userId,
                    enabled = body.enabled,
                    expiresAt = body.expiresAt,
                )
            )
            db.audit(user.tenantId, user.id.toString(), "TOKEN_SAVE", body.uid)
            MutationResponse()
        }
    }
}
