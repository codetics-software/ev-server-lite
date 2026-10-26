package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.services.stations.RegistrationTokenService
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
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
    private val registrations: RegistrationTokenService,
    private val certificates: ChargerCertificates,
    private val properties: AppProperties,
    private val stationEvents: StationEventRepository,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(gateway, "/ocpp/{tenant}/{station}")
            .addInterceptors(
                StationHandshake(stations, tenants, passwords, accounts, registrations, certificates, properties, stationEvents)
            )
    }
}

val STATION_ID = Regex("[A-Za-z0-9._:*-]{1,100}")
private val TENANT_SLUG = Regex("[a-z0-9-]{1,63}")

data class ConnectionSecurity(
    val profile: Int,
    val certificate: ClientCertificate? = null,
    val promoteSecret: Boolean = false,
)

private class StationHandshake(
    private val stations: StationRepository,
    private val tenants: TenantRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val registrations: RegistrationTokenService,
    private val certificates: ChargerCertificates,
    private val properties: AppProperties,
    private val stationEvents: StationEventRepository,
) : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Rejected(val reason: String, val certificate: ClientCertificate? = null) : RuntimeException(reason)

    private val reported = ConcurrentHashMap<String, Instant>()

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        handler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        var known: StationEntity? = null
        try {
            val path = request.uri.path.split('/').filter(String::isNotBlank)
            val protocols =
                request.headers["Sec-WebSocket-Protocol"].orEmpty().flatMap { header ->
                    header.split(',').map(String::trim)
                }
            require(path.size == 3 && (protocols.isEmpty() || "ocpp1.6" in protocols))

            val slug = path[1]
            val station = path[2]
            require(slug.matches(TENANT_SLUG))
            require(station.matches(STATION_ID) && station != "." && station != "..")

            val peer = request.remoteAddress?.address
            val proxied = certificates.trusted(peer)
            val client = certificates.clientAddress(peer, request.headers["X-Forwarded-For"].orEmpty())
            accounts.limit("ocpp:$client", 1000)
            accounts.limit("ocpp:$client:$slug:$station", 60)

            val tenant =
                tenants.findBySlugAndActiveTrue(slug)
                    ?: throw IllegalArgumentException("Espace client inconnu.")
            known =
                stations.findById(StationKey(tenant.id, station)).orElse(null)?.takeUnless {
                    it.deleted
                }

            val tls = secure(request, proxied)
            val password =
                try {
                    password(request.headers.getFirst("Authorization"), station)
                } catch (_: Exception) {
                    throw Rejected("en-tête d’authentification invalide")
                }

            val device =
                known ?: password?.let { registrations.register(tenant.id, station, it) }
                    ?: throw IllegalArgumentException("Borne inconnue.")
            if (!device.enabled) throw Rejected("borne désactivée")

            var promote = false
            val basic =
                when {
                    password == null -> false
                    passwords.matches(password, device.secretHash) -> true
                    device.pendingSecretHash?.let { passwords.matches(password, it) } == true -> {
                        promote = true
                        true
                    }
                    else -> throw Rejected("mot de passe invalide")
                }

            val presented = if (tls) presented(request, certificates.certificateProxy(peer)) else null
            val certificate =
                presented?.let {
                    try {
                        certificates.verify(
                            it,
                            station,
                            device.pinnedCertificateFingerprint,
                            device.issuedCertificateFingerprint,
                        )
                    } catch (refused: CertificateRefused) {
                        if (device.securityProfile >= 3) throw Rejected(refused.reason, refused.certificate)
                        null
                    }
                }

            val effective =
                when {
                    tls && certificate != null -> 3
                    tls && basic -> 2
                    basic -> 1
                    else -> 0
                }
            if (effective < device.securityProfile) {
                throw Rejected(
                    when {
                        device.securityProfile >= 2 && !tls -> "TLS requis"
                        device.securityProfile == 3 -> "certificat absent"
                        else -> "mot de passe absent"
                    }
                )
            }
            if (effective == 0 && !properties.ocpp.allowUnauthenticated) {
                throw Rejected("profil 0 désactivé sur le serveur")
            }

            attributes["station"] = StationIdentity(tenant.id, station)
            attributes["security"] = ConnectionSecurity(effective, certificate, promote)
            attributes["connection"] = UUID.randomUUID().toString()
            return true
        } catch (error: Exception) {
            log.debug("Connexion OCPP refusée : {}", error.message)
            if (error is Rejected) known?.let { reject(it, error) }
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
    }

    private fun reject(station: StationEntity, rejection: Rejected) {
        val key = "${station.key.tenantId}/${station.key.stationId}/${rejection.reason}"
        val now = Instant.now()
        val previous = reported[key]
        if (previous != null && previous.isAfter(now.minusSeconds(60))) return
        reported[key] = now
        if (reported.size > 10000) reported.entries.removeIf { it.value.isBefore(now.minusSeconds(60)) }
        runCatching {
            stationEvents.save(
                StationEventEntity(
                    tenantId = station.key.tenantId,
                    stationId = station.key.stationId,
                    type = "CONNECTION_REJECTED",
                    status = rejection.reason.take(60),
                    info =
                        rejection.certificate?.let { "${it.subject} / ${it.fingerprint}" },
                )
            )
        }
    }

    private fun password(authorization: String?, station: String): String? {
        if (authorization == null) return null
        require(authorization.startsWith("Basic ", ignoreCase = true) && authorization.length < 2048)
        val decoded = String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8)
        require(decoded.startsWith("$station:"))
        return decoded.removePrefix("$station:").takeIf(String::isNotEmpty)
    }

    private fun secure(request: ServerHttpRequest, proxied: Boolean): Boolean {
        val servlet = (request as? ServletServerHttpRequest)?.servletRequest
        if (servlet?.isSecure == true) return true
        if (!proxied) return false
        val forwarded = request.headers.getFirst("X-Forwarded-Proto")?.split(',')?.firstOrNull()?.trim()
        return forwarded.equals("https", true) || forwarded.equals("wss", true)
    }

    private fun presented(request: ServerHttpRequest, proxied: Boolean): List<X509Certificate>? {
        val servlet = (request as? ServletServerHttpRequest)?.servletRequest
        @Suppress("UNCHECKED_CAST")
        val direct = servlet?.getAttribute("jakarta.servlet.request.X509Certificate") as? Array<X509Certificate>
        if (!direct.isNullOrEmpty()) return direct.toList()

        val header = certificates.clientCertificateHeader
        if (!proxied || header.isEmpty()) return null
        val value = request.headers.getFirst(header)?.takeIf(String::isNotBlank) ?: return null
        return runCatching { certificates.fromHeader(value) }.getOrNull()?.takeIf(List<X509Certificate>::isNotEmpty)
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
