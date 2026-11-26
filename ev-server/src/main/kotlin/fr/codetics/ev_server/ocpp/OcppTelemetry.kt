package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.ocpp.v201.Ocpp201Messages
import io.micrometer.common.KeyValue
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Métriques et traces OpenTelemetry du trafic OCPP (via Micrometer, exportées en OTLP).
 *
 * Les étiquettes des métriques ne reprennent jamais une valeur libre envoyée par la borne :
 * une action inconnue devient `other`, l’identifiant de borne ne figure que dans les traces.
 * Aucune méthode ne lève d’exception : la supervision ne doit jamais couper une connexion.
 */
@Component
class OcppTelemetry(
    private val meters: MeterRegistry,
    private val observations: ObservationRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val actions: Set<String> =
        OcppValidation.inbound + OcppValidation.outbound + Ocpp201Messages.fromStation.keys + Ocpp201Messages.toStation.keys

    fun action(name: String?): String = name?.takeIf { it in actions } ?: "other"

    /** Enregistre la jauge des connexions ouvertes, par version OCPP. */
    fun connections(version: OcppVersion, count: () -> Number) {
        safely {
            Gauge.builder("ocpp.connections") { count().toDouble() }
                .description("Connexions WebSocket OCPP ouvertes")
                .tag("ocpp.version", version.label)
                .register(meters)
        }
    }

    /** Ouverture de connexion : `accepted`, ou `rejected` avec un motif à faible cardinalité. */
    fun handshake(outcome: String, reason: String, tls: Boolean, profile: Int? = null) {
        safely {
            Counter.builder("ocpp.handshakes")
                .description("Ouvertures de connexion OCPP")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .tag("tls", tls.toString())
                .tag("security.profile", profile?.toString() ?: "none")
                .register(meters)
                .increment()
        }
    }

    fun disconnected(version: OcppVersion, code: Int) {
        safely {
            Counter.builder("ocpp.disconnections")
                .description("Fermetures de connexion OCPP, par code WebSocket")
                .tag("ocpp.version", version.label)
                .tag("close.code", code.toString())
                .register(meters)
                .increment()
        }
    }

    /**
     * Trame reçue ou envoyée. [kind] vaut `call`, `result`, `error` ou `invalid`.
     */
    fun frame(direction: String, kind: String, action: String?, version: OcppVersion, bytes: Int) {
        safely {
            Counter.builder("ocpp.messages")
                .description("Trames OCPP")
                .tag("direction", direction)
                .tag("kind", kind)
                .tag("ocpp.action", action(action))
                .tag("ocpp.version", version.label)
                .register(meters)
                .increment()
            DistributionSummary.builder("ocpp.message.size")
                .description("Taille des trames OCPP")
                .baseUnit("bytes")
                .tag("direction", direction)
                .tag("ocpp.version", version.label)
                .register(meters)
                .record(bytes.toDouble())
        }
    }

    /**
     * Trace et chronomètre le traitement d’un appel de la borne (span `ocpp.call`).
     * Le résultat (`ok` ou code CallError) est ajouté par [outcome].
     */
    fun call(identity: StationIdentity, action: String, messageId: String, version: OcppVersion): Observation {
        return try {
            Observation.createNotStarted("ocpp.call", observations)
                .contextualName("OCPP ${action(action)}")
                .lowCardinalityKeyValue("ocpp.action", action(action))
                .lowCardinalityKeyValue("ocpp.version", version.label)
                .lowCardinalityKeyValue("ocpp.direction", "inbound")
                .highCardinalityKeyValue("ocpp.station", identity.station)
                .highCardinalityKeyValue("ocpp.tenant", identity.tenant.toString())
                .highCardinalityKeyValue("ocpp.message.id", messageId)
                .start()
        } catch (error: Exception) {
            log.debug("Observation OCPP indisponible", error)
            Observation.NOOP
        }
    }

    fun outcome(observation: Observation, result: String, error: Throwable? = null) {
        safely {
            observation.lowCardinalityKeyValue(KeyValue.of("ocpp.outcome", result))
            if (error != null) observation.error(error)
            observation.stop()
        }
    }

    /** Commande envoyée à la borne ou réponse reçue (`sent`, `accepted`, `error`, `failed`). */
    fun command(action: String?, version: OcppVersion, state: String) {
        safely {
            Counter.builder("ocpp.commands")
                .description("Commandes du serveur vers les bornes")
                .tag("ocpp.action", action(action))
                .tag("ocpp.version", version.label)
                .tag("state", state)
                .register(meters)
                .increment()
        }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            log.debug("Métrique OCPP non enregistrée", error)
        }
    }
}
