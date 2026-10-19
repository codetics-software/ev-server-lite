package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.security.AccountService
import java.util.Base64
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class OcppWebSocketConfiguration(
    private val gateway: OcppGateway,
    private val stations: StationRepository,
    private val tenants: TenantRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(gateway, "/ocpp/{tenant}/{station}")
            .addInterceptors(StationHandshake(stations, tenants, passwords, accounts))
    }
}

val STATION_ID = Regex("[A-Za-z0-9_-]{1,100}")
private val TENANT_SLUG = Regex("[a-z0-9-]{1,63}")

private class StationHandshake(
    private val stations: StationRepository,
    private val tenants: TenantRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        handler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        try {
            val path = request.uri.path.split('/').filter(String::isNotBlank)
            val protocols =
                request.headers["Sec-WebSocket-Protocol"].orEmpty().flatMap { header ->
                    header.split(',').map(String::trim)
                }
            require(path.size == 3 && "ocpp1.6" in protocols)

            val slug = path[1]
            val station = path[2]
            require(slug.matches(TENANT_SLUG))
            require(station.matches(STATION_ID))
            accounts.limit("ocpp:${request.remoteAddress?.address?.hostAddress}", 100)

            val authorization = requireNotNull(request.headers.getFirst("Authorization"))
            require(authorization.startsWith("Basic ") && authorization.length < 2048)
            val encoded = authorization.removePrefix("Basic ")
            val credentials = String(Base64.getDecoder().decode(encoded)).split(':', limit = 2)
            require(credentials.size == 2 && credentials[0] == station)

            val tenant =
                tenants.findBySlugAndActiveTrue(slug)
                    ?: throw IllegalArgumentException("Espace client inconnu.")
            val device = stations.findById(StationKey(tenant.id, station)).orElse(null)
            require(
                device != null &&
                    device.enabled &&
                    passwords.matches(credentials[1], device.secretHash)
            )

            attributes["station"] = StationIdentity(tenant.id, station)
            return true
        } catch (_: Exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        handler: WebSocketHandler,
        exception: Exception?,
    ) {
        // L’authentification précède l’acceptation de la connexion WebSocket.
    }
}
