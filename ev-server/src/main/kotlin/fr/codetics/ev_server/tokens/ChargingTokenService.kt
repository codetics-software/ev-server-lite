package fr.codetics.ev_server.tokens

import fr.codetics.ev_server.api.MutationResponse
import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.persistence.ChargingTokenEntity
import fr.codetics.ev_server.persistence.ChargingTokenKey
import fr.codetics.ev_server.persistence.ChargingTokenRepository
import fr.codetics.ev_server.persistence.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ChargingTokenService(private val db: Database, private val tokens: ChargingTokenRepository, private val users: UserRepository) {
    fun list(user: AppPrincipal): List<ChargingTokenResponse> =
        tokens.findAllByKeyTenantId(user.tenantId, PageRequest.of(0, 500)).content.map { token ->
            ChargingTokenResponse(token.key.uid, token.userId, token.type, token.enabled, token.expiresAt)
        }

    fun save(
        body: TokenInput,
        user: AppPrincipal
    ): MutationResponse {
        require(body.uid.matches(Regex("[A-Za-z0-9_-]{1,20}"))) {
            "OCPP 1.6 idTag must contain at most 20 characters"
        }
        if (body.userId != null && users.findByTenantIdAndId(user.tenantId, body.userId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is unavailable")
        }

        return db.atomic {
            tokens.save(
                ChargingTokenEntity(
                    key = ChargingTokenKey(user.tenantId, body.uid),
                    userId = body.userId,
                    enabled = body.enabled,
                    expiresAt = body.expiresAt
                )
            )
            db.audit(user.tenantId, user.id.toString(), "TOKEN_SAVE", body.uid)
            MutationResponse()
        }
    }
}
