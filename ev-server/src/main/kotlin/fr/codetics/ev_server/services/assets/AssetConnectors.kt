package fr.codetics.ev_server.services.assets

import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.models.organization.AssetConnectionData
import fr.codetics.ev_server.models.organization.AssetData
import java.math.BigDecimal
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

data class AssetSecrets(val password: String? = null, val clientSecret: String? = null, val apiKey: String? = null)

data class AssetSample(val at: Instant, val powerW: BigDecimal, val energyWh: BigDecimal? = null, val soc: Int? = null)

class AssetConnectionError(message: String) : RuntimeException(message)

interface AssetConnector {
    val type: String

    fun check(connection: AssetConnectionData, secrets: AssetSecrets)

    fun fetch(asset: AssetData, connection: AssetConnectionData, secrets: AssetSecrets, since: Instant?, manual: Boolean): List<AssetSample>
}

@Component
class AssetHttp(private val json: ObjectMapper) {
    private val client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val tokens = ConcurrentHashMap<String, Pair<String, Instant>>()

    fun uri(base: String, path: String = ""): URI {
        val uri = URI(base.trimEnd('/') + path)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
            throw InvalidRequest("L’adresse de connexion doit utiliser HTTPS.")
        }
        val addresses = runCatching { InetAddress.getAllByName(uri.host).toList() }.getOrElse {
            throw AssetConnectionError("Le serveur de l’équipement est introuvable.")
        }
        if (addresses.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }) {
            throw InvalidRequest("L’adresse de connexion doit être publique.")
        }
        return uri
    }

    fun form(values: Map<String, String?>) =
        values.filterValues { it != null }.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, Charsets.UTF_8) + "=" + URLEncoder.encode(value, Charsets.UTF_8)
        }

    fun send(request: HttpRequest.Builder): JsonNode {
        val response =
            try {
                client.send(request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofInputStream())
            } catch (_: Exception) {
                throw AssetConnectionError("Le service de l’équipement n’a pas pu être contacté.")
            }
        val bytes = response.body().use { it.readNBytes(2_097_153) }
        if (response.statusCode() !in 200..299) {
            throw AssetConnectionError("Le service de l’équipement a répondu ${response.statusCode()}.")
        }
        if (bytes.size > 2_097_152) throw AssetConnectionError("La réponse de l’équipement est trop volumineuse.")
        if (bytes.isEmpty()) return json.createObjectNode()
        return runCatching { json.readTree(bytes) }.getOrElse { throw AssetConnectionError("La réponse de l’équipement est illisible.") }
    }

    fun token(key: String, fetch: () -> Pair<String, Long>): String {
        tokens[key]?.takeIf { it.second.isAfter(Instant.now().plusSeconds(30)) }?.let { return it.first }
        val (token, lifetime) = fetch()
        tokens[key] = token to Instant.now().plusSeconds(lifetime.coerceIn(60, 86400))
        return token
    }

    fun forget(key: String) {
        tokens.remove(key)
    }
}

private fun JsonNode.decimal(field: String): BigDecimal? =
    get(field)?.takeIf { it.isNumber || it.isString }?.asText()?.replace(',', '.')?.toBigDecimalOrNull()

private fun required(value: String?, label: String) =
    value?.takeIf(String::isNotBlank) ?: throw InvalidRequest("$label est requis pour cette connexion.")

private fun direction(asset: AssetData) = if (asset.producing()) BigDecimal.ONE.negate() else BigDecimal.ONE

@Component
class SchneiderConnector(private val http: AssetHttp) : AssetConnector {
    override val type = "SCHNEIDER"

    private fun token(connection: AssetConnectionData, secrets: AssetSecrets): String {
        val url = required(connection.url, "L’adresse")
        return http.token("schneider:$url:${connection.user}") {
            val response =
                http.send(
                    HttpRequest.newBuilder(http.uri(url, "/GetToken"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                http.form(
                                    mapOf(
                                        "grant_type" to "password",
                                        "username" to required(connection.user, "L’utilisateur"),
                                        "password" to required(secrets.password, "Le mot de passe"),
                                    )
                                )
                            )
                        )
                )
            (response.path("access_token").asText().ifBlank { throw AssetConnectionError("Identifiants Schneider refusés.") }) to
                response.path("expires_in").asLong(600)
        }
    }

    override fun check(connection: AssetConnectionData, secrets: AssetSecrets) {
        token(connection, secrets)
    }

    override fun fetch(asset: AssetData, connection: AssetConnectionData, secrets: AssetSecrets, since: Instant?, manual: Boolean): List<AssetSample> {
        val token = token(connection, secrets)
        val data =
            http.send(
                HttpRequest.newBuilder(http.uri(required(connection.url, "L’adresse"), "/${required(connection.meterId, "Le compteur")}"))
                    .header("Authorization", "Bearer $token")
                    .GET()
            )
        fun property(name: String) = data.firstOrNull { it.path("Name").asText() == name }?.decimal("Value") ?: BigDecimal.ZERO
        return listOf(
            AssetSample(
                Instant.now(),
                property("PActive_Tot") * BigDecimal(1000) * direction(asset),
                property("Energie_Active") * BigDecimal(1000),
            )
        )
    }
}

@Component
class GreencomConnector(private val http: AssetHttp, private val json: ObjectMapper) : AssetConnector {
    override val type = "GREENCOM"

    private fun token(connection: AssetConnectionData, secrets: AssetSecrets): String {
        val url = required(connection.url, "L’adresse")
        val client = required(connection.clientId ?: connection.user, "L’identifiant client")
        return http.token("greencom:$url:$client") {
            val body =
                json.writeValueAsString(
                    mapOf(
                        "grant_type" to "client_credentials",
                        "client_id" to client,
                        "client_secret" to required(secrets.clientSecret ?: secrets.password, "Le secret client"),
                    )
                )
            val response =
                http.send(
                    HttpRequest.newBuilder(http.uri(url, "/authentication-api/tokens"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                )
            (response.path("access_token").asText().ifBlank { throw AssetConnectionError("Identifiants GreenCom refusés.") }) to
                response.path("expires_in").asLong(600)
        }
    }

    override fun check(connection: AssetConnectionData, secrets: AssetSecrets) {
        token(connection, secrets)
    }

    override fun fetch(asset: AssetData, connection: AssetConnectionData, secrets: AssetSecrets, since: Instant?, manual: Boolean): List<AssetSample> {
        val token = token(connection, secrets)
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val from = if (manual || since == null) now.minus(Duration.ofMinutes(1)) else since
        val data =
            http.send(
                HttpRequest.newBuilder(
                        http.uri(
                            required(connection.url, "L’adresse"),
                            "/site-api/${required(connection.meterId, "Le compteur")}?withEnergy=true&withPower=true&from=$from&to=$now&step=PT1M",
                        )
                    )
                    .header("Authorization", "Bearer $token")
                    .GET()
            )
        val combined = asset.type == "CONSUMPTION_AND_PRODUCTION"
        val power = if (combined) data.path("power").path("charge").path("data") else data.path("power").path("data")
        if (power.isArray && power.size() > 0) {
            return power.mapIndexedNotNull { index, point ->
                val at = runCatching { Instant.parse(point.path("timestamp").asText()).plus(Duration.ofMinutes(1)) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val watts =
                    if (combined) {
                        (point.decimal("value") ?: BigDecimal.ZERO) -
                            (data.path("power").path("discharge").path("data").path(index).decimal("value") ?: BigDecimal.ZERO)
                    } else {
                        (point.decimal("value") ?: BigDecimal.ZERO) * direction(asset)
                    }
                val soc = data.path("percent").path("soc").path("data").path(index).decimal("value")?.toInt()
                AssetSample(at, watts, null, soc)
            }
        }
        val watts =
            if (combined) {
                (data.path("power").path("charge").decimal("average") ?: BigDecimal.ZERO) -
                    (data.path("power").path("discharge").decimal("average") ?: BigDecimal.ZERO)
            } else {
                (data.path("power").decimal("average") ?: return emptyList()) * direction(asset)
            }
        return listOf(AssetSample(now, watts, null, data.path("percent").path("soc").decimal("current")?.toInt()))
    }
}

@Component
class IothinkConnector(private val http: AssetHttp) : AssetConnector {
    override val type = "IOTHINK"
    private val reference = LocalDate.of(2000, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    private fun token(connection: AssetConnectionData, secrets: AssetSecrets): String {
        val url = required(connection.url, "L’adresse")
        return http.token("iothink:$url:${connection.user}") {
            val response =
                http.send(
                    HttpRequest.newBuilder(http.uri(url, "/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                http.form(
                                    mapOf(
                                        "grant_type" to "password",
                                        "username" to required(connection.user, "L’utilisateur"),
                                        "password" to required(secrets.password, "Le mot de passe"),
                                    )
                                )
                            )
                        )
                )
            (response.path("access_token").asText().ifBlank { throw AssetConnectionError("Identifiants IoThink refusés.") }) to
                response.path("expires_in").asLong(600)
        }
    }

    override fun check(connection: AssetConnectionData, secrets: AssetSecrets) {
        token(connection, secrets)
    }

    override fun fetch(asset: AssetData, connection: AssetConnectionData, secrets: AssetSecrets, since: Instant?, manual: Boolean): List<AssetSample> {
        val token = token(connection, secrets)
        val start = since ?: Instant.now().truncatedTo(ChronoUnit.DAYS)
        val seconds = Duration.between(reference, start).seconds
        val data =
            http.send(
                HttpRequest.newBuilder(
                        http.uri(required(connection.url, "L’adresse"), "/${required(connection.meterId, "Le compteur")}&startTime=$seconds")
                    )
                    .header("Authorization", "Bearer $token")
                    .GET()
            )
        val merged = sortedMapOf<Long, MutableMap<String, BigDecimal>>()
        data.path("historics").forEach { set ->
            val tag = set.path("tagReference").asText()
            set.path("logs").forEach { log ->
                val value = log.decimal("value") ?: return@forEach
                merged.getOrPut(log.path("timestamp").asLong()) { mutableMapOf() }[tag] = value
            }
        }
        val samples =
            merged.mapNotNull { (timestamp, values) ->
                fun value(name: String) = values[name] ?: BigDecimal.ZERO
                val hasPower = listOf("io-pow-active", "io-pow-l1", "io-pow-l2", "io-pow-l3").any { it in values }
                if (!hasPower) return@mapNotNull null
                val kilowatts =
                    values["io-pow-active"] ?: (value("io-pow-l1") + value("io-pow-l2") + value("io-pow-l3"))
                val combined = asset.type == "CONSUMPTION_AND_PRODUCTION"
                AssetSample(
                    reference.plusSeconds(timestamp),
                    kilowatts * BigDecimal(1000) * (if (combined) BigDecimal.ONE else direction(asset)),
                    if (combined) null
                    else (values["io-energy-input"] ?: (value("io-energy-l1") + value("io-energy-l2") + value("io-energy-l3"))) * BigDecimal(1000),
                    values["io-soc"]?.toInt(),
                )
            }
        return if (manual) samples.takeLast(1) else samples
    }
}

@Component
class LacroixConnector(private val http: AssetHttp) : AssetConnector {
    override val type = "LACROIX"

    private fun basic(connection: AssetConnectionData, secrets: AssetSecrets) =
        "Basic " +
            Base64.getEncoder()
                .encodeToString("${required(connection.user, "L’utilisateur")}:${required(secrets.password, "Le mot de passe")}".toByteArray())

    override fun check(connection: AssetConnectionData, secrets: AssetSecrets) {
        http.send(
            HttpRequest.newBuilder(http.uri(required(connection.url, "L’adresse"), "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        http.form(mapOf("email" to connection.user, "plainPassword" to required(secrets.password, "Le mot de passe")))
                    )
                )
        )
    }

    override fun fetch(asset: AssetData, connection: AssetConnectionData, secrets: AssetSecrets, since: Instant?, manual: Boolean): List<AssetSample> {
        val minutes = since?.let { Duration.between(it, Instant.now()).toMinutes() } ?: Long.MAX_VALUE
        val period =
            when {
                manual -> "last"
                minutes <= 5 -> "5m"
                minutes <= 60 -> "1h"
                else -> "day"
            }
        val data =
            http.send(
                HttpRequest.newBuilder(
                        http.uri(required(connection.url, "L’adresse"), "/${required(connection.meterId, "Le compteur")}/getPowerData?period=$period&group_by=1m")
                    )
                    .header("Authorization", basic(connection, secrets))
                    .GET()
            )
        val samples =
            data.path("data").mapNotNull { point ->
                val at = runCatching { Instant.parse(point.path("date").asText()) }.getOrNull() ?: return@mapNotNull null
                if (since != null && !at.isAfter(since)) return@mapNotNull null
                val watts = point.decimal("powerApparentConsumedTotal") ?: return@mapNotNull null
                AssetSample(at, watts, watts.multiply(BigDecimal(60)))
            }
        return if (manual) samples.takeLast(1) else samples
    }
}

@Component
class WitConnector(private val http: AssetHttp) : AssetConnector {
    override val type = "WIT"

    private fun token(connection: AssetConnectionData, secrets: AssetSecrets): String {
        val authentication = required(connection.authenticationUrl, "L’adresse d’authentification")
        return http.token("wit:$authentication:${connection.user}") {
            val response =
                http.send(
                    HttpRequest.newBuilder(http.uri(authentication, "/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                http.form(
                                    mapOf(
                                        "client_id" to required(connection.clientId, "L’identifiant client"),
                                        "client_secret" to required(secrets.clientSecret, "Le secret client"),
                                        "grant_type" to "password",
                                        "username" to required(connection.user, "L’utilisateur"),
                                        "password" to required(secrets.password, "Le mot de passe"),
                                        "scope" to "https://api.wit-datacenter.com",
                                    )
                                )
                            )
                        )
                )
            (response.path("access_token").asText().ifBlank { throw AssetConnectionError("Identifiants WIT refusés.") }) to
                response.path("expires_in").asLong(600)
        }
    }

    override fun check(connection: AssetConnectionData, secrets: AssetSecrets) {
        token(connection, secrets)
    }

    override fun fetch(asset: AssetData, connection: AssetConnectionData, secrets: AssetSecrets, since: Instant?, manual: Boolean): List<AssetSample> {
        if (asset.type == "CONSUMPTION_AND_PRODUCTION") {
            throw AssetConnectionError("La connexion WIT ne prend pas en charge les équipements mixtes.")
        }
        val token = token(connection, secrets)
        val from =
            if (manual || since == null) Instant.now().minusSeconds(connection.refreshSeconds.toLong())
            else since
        val data =
            http.send(
                HttpRequest.newBuilder(http.uri(required(connection.url, "L’adresse"), "/${required(connection.meterId, "Le compteur")}?From=$from"))
                    .header("Authorization", "Bearer $token")
                    .GET()
            )
        val samples =
            data.mapNotNull { point ->
                val at = runCatching { Instant.parse(point.path("T").asText()) }.getOrNull() ?: return@mapNotNull null
                val kilowatts = point.decimal("V") ?: return@mapNotNull null
                AssetSample(at, kilowatts * BigDecimal(1000) * direction(asset))
            }
        return if (manual) samples.takeLast(1) else samples
    }
}
