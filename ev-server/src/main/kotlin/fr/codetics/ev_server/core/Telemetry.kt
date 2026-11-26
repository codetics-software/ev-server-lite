package fr.codetics.ev_server.core

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import fr.codetics.ev_server.ocpp.OcppGateway
import io.micrometer.observation.ObservationPredicate
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.scheduling.support.ScheduledTaskObservationContext

/**
 * Compléments OpenTelemetry. L’export OTLP (traces, métriques, journaux) est configuré dans
 * application.yml et reste désactivé tant que OTEL_ENABLED ne vaut pas true.
 */
@Configuration(proxyBeanMethods = false)
class Telemetry {
    /**
     * Ni trace ni mesure pour les sondes de santé (toutes les 15 s) ni pour la boucle d’envoi
     * des commandes OCPP (chaque seconde) : elles noieraient les requêtes et messages réels.
     */
    @Bean
    fun skipNoise(): ObservationPredicate = ObservationPredicate { _, context ->
        when (context) {
            is ServerRequestObservationContext -> context.carrier?.requestURI?.startsWith("/actuator") != true
            is ScheduledTaskObservationContext ->
                !(OcppGateway::class.java.isAssignableFrom(context.targetClass) && context.method.name == "dispatch")
            else -> true
        }
    }

    /**
     * Transmet les journaux Logback au SDK OpenTelemetry (export OTLP des logs, corrélés aux traces).
     * Ajouté par programme pour conserver la configuration Logback de Spring Boot, y compris
     * LOGGING_STRUCTURED_FORMAT_CONSOLE.
     */
    @Bean
    @ConditionalOnProperty(name = ["management.logging.export.otlp.enabled"], havingValue = "true")
    fun otlpLogs(openTelemetry: OpenTelemetry): DisposableBean {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return DisposableBean {}
        val appender =
            OpenTelemetryAppender().apply {
                name = "OTEL"
                this.context = context
                setCaptureExperimentalAttributes(false)
                setCaptureCodeAttributes(false)
                setCaptureMdcAttributes("*")
                start()
            }
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        root.addAppender(appender)
        OpenTelemetryAppender.install(openTelemetry)
        return DisposableBean {
            root.detachAppender(appender)
            appender.stop()
        }
    }
}
