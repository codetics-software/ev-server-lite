package fr.codetics.ev_server.core

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Pings the server's own public URL so hosts that sleep idle instances (e.g. Render free plan)
 * keep it awake. Disabled unless KEEP_ALIVE_URL is set.
 */
@Component
class KeepAlive(@Value("\${app.keep-alive-url:}") private val url: String) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Scheduled(initialDelay = 1, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun ping() {
        if (url.isBlank()) return
        runCatching {
                val request =
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build()
                client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            }
            .onSuccess { log.debug("Keep-alive ping {} -> {}", url, it) }
            .onFailure { log.warn("Keep-alive ping {} failed: {}", url, it.message) }
    }
}
