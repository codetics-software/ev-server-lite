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
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.server.support.DefaultHandshakeHandler

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
    private val telemetry: OcppTelemetry,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(gateway, "/ocpp/{tenant}/{station}")
            .setHandshakeHandler(StationProtocol())
            .addInterceptors(
                StationHandshake(
                    stations,
                    tenants,
                    passwords,
                    accounts,
                    registrations,
                    certificates,
                    properties,
                    stationEvents,
                    telemetry,
                )
            )
    }
}

val STATION_ID = Regex("[A-Za-z0-9._:*-]{1,100}")
const val UNSUPPORTED_PROTOCOL = "unsupportedProtocol"
private val TENANT_SLUG = Regex("[a-z0-9-]{1,63}")

private val negotiated = ThreadLocal<OcppVersion?>()

private class StationProtocol : DefaultHandshakeHandler() {
    override fun selectProtocol(requestedProtocols: List<String>, webSocketHandler: WebSocketHandler): String? {
        val chosen = negotiated.get() ?: return null
        return requestedProtocols.firstOrNull { it.equals(chosen.subprotocol, true) }
    }
}

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
    private val telemetry: OcppTelemetry,
) : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    /** [reason] est consigné dans les événements de la borne, [code] sert d’étiquette de métrique. */
    private class Rejected(val reason: String, val code: String, val certificate: ClientCertificate? = null) :
        RuntimeException(reason)

    private val reported = ConcurrentHashMap<String, Instant>()

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        handler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        var known: StationEntity? = null
        var tls = false
        var target = request.uri.path
        negotiated.remove()
        try {
            val path = request.uri.path.split('/').filter(String::isNotBlank)
            val protocols =
                request.headers["Sec-WebSocket-Protocol"].orEmpty().flatMap { header ->
                    header.split(',').map(String::trim)
                }
            if (path.size != 3) throw Rejected("adresse invalide", "bad_path")

            val slug = path[1]
            val station = path[2]
            if (!slug.matches(TENANT_SLUG)) throw Rejected("espace client invalide", "bad_path")
            if (!station.matches(STATION_ID) || station == "." || station == "..") {
                throw Rejected("identifiant de borne invalide", "bad_station_id")
            }
            target = "$slug/$station"

            val peer = request.remoteAddress?.address
            val proxied = certificates.trusted(peer)
            val client = certificates.clientAddress(peer, request.headers["X-Forwarded-For"].orEmpty())
            accounts.limit("ocpp:$client", 1000)
            accounts.limit("ocpp:$client:$slug:$station", 60)

            val tenant =
                tenants.findBySlugAndActiveTrue(slug) ?: throw Rejected("espace client inconnu", "unknown_tenant")
            known =
                stations.findById(StationKey(tenant.id, station)).orElse(null)?.takeUnless {
                    it.deleted
                }

            tls = secure(request, proxied)
            // Un en-tête illisible n’est refusé qu’une fois la borne connue : une borne en profil 0
            // n’est pas authentifiée, l’en-tête résiduel que certains micrologiciels envoient
            // (« Basic » vide, utilisateur par défaut du fabricant) ne doit pas la bloquer.
            val header = request.headers.getFirst("Authorization")
            val parsed = runCatching { password(header, station) }
            val password = parsed.getOrNull()

            val device =
                known ?: password?.let { registrations.register(tenant.id, station, it) }
                    ?: throw Rejected("borne inconnue", "unknown_station")
            if (!device.enabled) throw Rejected("borne désactivée", "station_disabled")
            val open = device.securityProfile == 0 && properties.ocpp.allowUnauthenticated
            if (parsed.isFailure && !open) {
                throw Rejected("en-tête d’authentification invalide", "invalid_authorization")
            }

            var promote = false
            val basic =
                when {
                    password == null -> false
                    passwords.matches(password, device.secretHash) -> true
                    device.pendingSecretHash?.let { passwords.matches(password, it) } == true -> {
                        promote = true
                        true
                    }
                    open -> false
                    else -> throw Rejected("mot de passe invalide", "invalid_password")
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
                        if (device.securityProfile >= 3) {
                            throw Rejected(refused.reason, "certificate_refused", refused.certificate)
                        }
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
                throw when {
                    device.securityProfile >= 2 && !tls -> Rejected("TLS requis", "tls_required")
                    device.securityProfile == 3 -> Rejected("certificat absent", "certificate_missing")
                    else -> Rejected("mot de passe absent", "password_missing")
                }
            }
            if (effective == 0 && !properties.ocpp.allowUnauthenticated) {
                throw Rejected("profil 0 désactivé sur le serveur", "profile0_disabled")
            }

            attributes["station"] = StationIdentity(tenant.id, station)
            val version = OcppVersion.negotiate(device.ocppProtocol, protocols)
            if (version == null) {
                val refusal =
                    Rejected(
                        if (device.ocppProtocol == OcppVersion.AUTO) "aucune version OCPP prise en charge proposée"
                        else "OCPP ${device.ocppProtocol} non proposé par la borne",
                        "unsupported_protocol",
                    )
                reject(device, refusal)
                report(target, refusal, tls)
                attributes[UNSUPPORTED_PROTOCOL] = refusal.reason
                return true
            }
            attributes["security"] = ConnectionSecurity(effective, certificate, promote)
            attributes["connection"] = UUID.randomUUID().toString()
            attributes["ocpp"] = version
            negotiated.set(version)
            telemetry.handshake("accepted", "none", tls, effective)
            return true
        } catch (error: Exception) {
            val rejection =
                when (error) {
                    is Rejected -> error
                    is ResponseStatusException if error.statusCode.value() == 429 ->
                        Rejected("trop de tentatives de connexion", "rate_limited")
                    else -> Rejected("requête invalide", "bad_request")
                }
            if (error !is Rejected && rejection.code == "bad_request") {
                log.debug("Ouverture OCPP illisible ({})", target, error)
            }
            known?.let { reject(it, rejection) }
            report(target, rejection, tls)
            response.setStatusCode(
                if (rejection.code == "rate_limited") HttpStatus.TOO_MANY_REQUESTS else HttpStatus.UNAUTHORIZED
            )
            return false
        }
    }

    /**
     * Journal visible de l’exploitant (INFO, une ligne par borne et motif par minute) et métrique.
     * Le motif ne contient jamais de secret.
     */
    private fun report(target: String, rejection: Rejected, tls: Boolean) {
        telemetry.handshake("rejected", rejection.code, tls)
        val key = "log/$target/${rejection.code}"
        val now = Instant.now()
        val previous = reported[key]
        if (previous != null && previous.isAfter(now.minusSeconds(60))) return
        reported[key] = now
        if (reported.size > 10000) reported.entries.removeIf { it.value.isBefore(now.minusSeconds(60)) }
        log.info("Connexion OCPP refusée ({}, TLS {}) : {}", target.take(170), if (tls) "oui" else "non", rejection.reason)
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
        if (authorization.isNullOrBlank()) return null
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
        negotiated.remove()
    }
}
