package fr.codetics.ev_server.services.assets

import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.models.organization.AssetConnectionData
import fr.codetics.ev_server.models.organization.AssetData
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class AssetConnectorsTests {
    private val json = JsonMapper.builder().build()

    private class FakeHttp(json: JsonMapper, private val route: (HttpRequest) -> Any) : AssetHttp(json) {
        private val mapper = json
        val requests = mutableListOf<HttpRequest>()

        override fun uri(base: String, path: String): URI = URI(base.trimEnd('/') + path)

        override fun send(request: HttpRequest.Builder): JsonNode {
            val built = request.build()
            requests += built
            return mapper.valueToTree(route(built))
        }
    }

    private fun connection(type: String, user: String? = "operator", clientId: String? = null, authenticationUrl: String? = null) =
        AssetConnectionData(
            type = type,
            url = "https://meter.example/api",
            authenticationUrl = authenticationUrl,
            meterId = "M1",
            user = user,
            clientId = clientId,
            refreshSeconds = 300,
        )

    private fun asset(type: String, connection: AssetConnectionData) = AssetData(type = type, connection = connection)

    private fun decimal(value: String) = BigDecimal(value)

    private fun assertSame(expected: String, actual: BigDecimal?) = assertEquals(0, decimal(expected).compareTo(actual), "$expected != $actual")

    @Test
    fun `Schneider lit puissance et energie et reutilise son jeton`() {
        val http =
            FakeHttp(json) { request ->
                if (request.uri().path.endsWith("/GetToken")) mapOf("access_token" to "T1", "expires_in" to 3600)
                else listOf(mapOf("Name" to "PActive_Tot", "Value" to "2,5"), mapOf("Name" to "Energie_Active", "Value" to 10))
            }
        val connector = SchneiderConnector(http)
        val connection = connection("SCHNEIDER")
        val secrets = AssetSecrets(password = "secret")

        val consumption = connector.fetch(asset("CONSUMPTION", connection), connection, secrets, null, true).single()
        assertSame("2500", consumption.powerW)
        assertSame("10000", consumption.energyWh)
        val production = connector.fetch(asset("PRODUCTION", connection), connection, secrets, null, true).single()
        assertSame("-2500", production.powerW)

        assertEquals(1, http.requests.count { it.uri().path.endsWith("/GetToken") })
        assertEquals("Bearer T1", http.requests.last().headers().firstValue("Authorization").orElse(""))
        assertEquals("https://meter.example/api/M1", http.requests.last().uri().toString())
        assertEquals("SCHNEIDER", connector.type)
    }

    @Test
    fun `un jeton refuse ou des identifiants manquants sont signales`() {
        val refused = SchneiderConnector(FakeHttp(json) { mapOf("error" to "invalid_grant") })
        assertThrows(AssetConnectionError::class.java) { refused.check(connection("SCHNEIDER", user = "other"), AssetSecrets(password = "x")) }
        assertThrows(InvalidRequest::class.java) { refused.check(connection("SCHNEIDER", user = null), AssetSecrets(password = "x")) }
        assertThrows(InvalidRequest::class.java) { refused.check(connection("SCHNEIDER", user = "third"), AssetSecrets()) }
        assertThrows(AssetConnectionError::class.java) {
            IothinkConnector(FakeHttp(json) { emptyMap<String, Any>() }).check(connection("IOTHINK", user = "nobody"), AssetSecrets(password = "x"))
        }
        assertThrows(AssetConnectionError::class.java) {
            GreencomConnector(FakeHttp(json) { emptyMap<String, Any>() }, json).check(connection("GREENCOM", clientId = "nobody"), AssetSecrets(clientSecret = "x"))
        }
    }

    @Test
    fun `GreenCom convertit les series et les batteries`() {
        val series =
            mapOf(
                "power" to
                    mapOf(
                        "data" to listOf(mapOf("timestamp" to "2026-03-01T10:00:00Z", "value" to 1200), mapOf("timestamp" to "bad", "value" to 1)),
                        "charge" to mapOf("data" to listOf(mapOf("timestamp" to "2026-03-01T10:00:00Z", "value" to 900))),
                        "discharge" to mapOf("data" to listOf(mapOf("value" to 300))),
                    ),
                "percent" to mapOf("soc" to mapOf("data" to listOf(mapOf("value" to 55)))),
            )
        val http =
            FakeHttp(json) { request ->
                if (request.uri().path.endsWith("/tokens")) mapOf("access_token" to "G1", "expires_in" to 600) else series
            }
        val connector = GreencomConnector(http, json)
        val connection = connection("GREENCOM", clientId = "client-series")
        val secrets = AssetSecrets(clientSecret = "secret")

        val consumption = connector.fetch(asset("CONSUMPTION", connection), connection, secrets, Instant.parse("2026-03-01T09:00:00Z"), false)
        assertEquals(1, consumption.size)
        assertEquals(Instant.parse("2026-03-01T10:01:00Z"), consumption.single().at)
        assertSame("1200", consumption.single().powerW)
        assertEquals(55, consumption.single().soc)
        assertTrue(http.requests.last().uri().query.contains("from=2026-03-01T09:00:00Z"))

        val battery = connector.fetch(asset("CONSUMPTION_AND_PRODUCTION", connection), connection, secrets, null, true).single()
        assertSame("600", battery.powerW)

        val average =
            GreencomConnector(
                FakeHttp(json) { request ->
                    if (request.uri().path.endsWith("/tokens")) mapOf("access_token" to "G2")
                    else mapOf("power" to mapOf("average" to 800, "charge" to mapOf("average" to 500), "discharge" to mapOf("average" to 100)), "percent" to mapOf("soc" to mapOf("current" to 40)))
                },
                json,
            )
        val averageConnection = connection("GREENCOM", clientId = "client-average")
        assertSame("-800", average.fetch(asset("PRODUCTION", averageConnection), averageConnection, secrets, null, true).single().powerW)
        val combined = average.fetch(asset("CONSUMPTION_AND_PRODUCTION", averageConnection), averageConnection, secrets, null, true).single()
        assertSame("400", combined.powerW)
        assertEquals(40, combined.soc)

        val empty = GreencomConnector(FakeHttp(json) { request -> if (request.uri().path.endsWith("/tokens")) mapOf("access_token" to "G3") else emptyMap<String, Any>() }, json)
        val emptyConnection = connection("GREENCOM", clientId = "client-empty")
        assertTrue(empty.fetch(asset("CONSUMPTION", emptyConnection), emptyConnection, secrets, null, true).isEmpty())
    }

    @Test
    fun `IoThink fusionne les historiques par horodatage`() {
        val reference = LocalDate.of(2000, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val http =
            FakeHttp(json) { request ->
                if (request.uri().path.endsWith("/token")) mapOf("access_token" to "I1")
                else
                    mapOf(
                        "historics" to
                            listOf(
                                mapOf("tagReference" to "io-pow-active", "logs" to listOf(mapOf("timestamp" to 100, "value" to 2))),
                                mapOf("tagReference" to "io-energy-input", "logs" to listOf(mapOf("timestamp" to 100, "value" to 5))),
                                mapOf(
                                    "tagReference" to "io-pow-l1",
                                    "logs" to listOf(mapOf("timestamp" to 200, "value" to 1), mapOf("timestamp" to 300, "value" to "n/a")),
                                ),
                                mapOf("tagReference" to "io-pow-l2", "logs" to listOf(mapOf("timestamp" to 200, "value" to 1.5))),
                                mapOf("tagReference" to "io-soc", "logs" to listOf(mapOf("timestamp" to 200, "value" to 80), mapOf("timestamp" to 400, "value" to 81))),
                            )
                    )
            }
        val connector = IothinkConnector(http)
        val connection = connection("IOTHINK", user = "iothink-user")
        val secrets = AssetSecrets(password = "secret")

        val samples = connector.fetch(asset("CONSUMPTION", connection), connection, secrets, reference.plusSeconds(50), false)
        assertEquals(2, samples.size)
        assertEquals(reference.plusSeconds(100), samples[0].at)
        assertSame("2000", samples[0].powerW)
        assertSame("5000", samples[0].energyWh)
        assertSame("2500", samples[1].powerW)
        assertEquals(80, samples[1].soc)
        assertTrue(http.requests.last().uri().toString().endsWith("startTime=50"))

        val latest = connector.fetch(asset("PRODUCTION", connection), connection, secrets, null, true)
        assertEquals(1, latest.size)
        assertSame("-2500", latest.single().powerW)
        val combined = connector.fetch(asset("CONSUMPTION_AND_PRODUCTION", connection), connection, secrets, null, true).single()
        assertSame("2500", combined.powerW)
        assertNull(combined.energyWh)
    }

    @Test
    fun `Lacroix choisit la periode selon l anciennete et filtre les mesures connues`() {
        val http =
            FakeHttp(json) { request ->
                if (request.uri().path.endsWith("/login")) emptyMap<String, Any>()
                else
                    mapOf(
                        "data" to
                            listOf(
                                mapOf("date" to "2026-03-01T10:00:00Z", "powerApparentConsumedTotal" to 100),
                                mapOf("date" to "2026-03-01T10:01:00Z", "powerApparentConsumedTotal" to 200),
                                mapOf("date" to "invalid", "powerApparentConsumedTotal" to 300),
                                mapOf("date" to "2026-03-01T10:02:00Z"),
                            )
                    )
            }
        val connector = LacroixConnector(http)
        val connection = connection("LACROIX")
        val secrets = AssetSecrets(password = "secret")

        connector.check(connection, secrets)
        assertTrue(http.requests.last().uri().path.endsWith("/login"))

        val filtered = connector.fetch(asset("CONSUMPTION", connection), connection, secrets, Instant.parse("2026-03-01T10:00:00Z"), false)
        assertEquals(1, filtered.size)
        assertSame("200", filtered.single().powerW)
        assertSame("12000", filtered.single().energyWh)
        assertTrue(http.requests.last().headers().firstValue("Authorization").orElse("").startsWith("Basic "))

        fun period(since: Instant?, manual: Boolean): String {
            connector.fetch(asset("CONSUMPTION", connection), connection, secrets, since, manual)
            return http.requests.last().uri().query.substringAfter("period=").substringBefore('&')
        }
        assertEquals("last", period(null, true))
        assertEquals("5m", period(Instant.now().minus(Duration.ofMinutes(3)), false))
        assertEquals("1h", period(Instant.now().minus(Duration.ofMinutes(30)), false))
        assertEquals("day", period(null, false))
        assertEquals(1, connector.fetch(asset("CONSUMPTION", connection), connection, secrets, null, true).size)
    }

    @Test
    fun `WIT lit des kilowatts et refuse les equipements mixtes`() {
        val http =
            FakeHttp(json) { request ->
                if (request.uri().path.endsWith("/token")) mapOf("access_token" to "W1")
                else listOf(mapOf("T" to "2026-03-01T10:00:00Z", "V" to 1.5), mapOf("T" to "2026-03-01T10:05:00Z", "V" to 2), mapOf("T" to "bad", "V" to 1))
            }
        val connector = WitConnector(http)
        val connection = connection("WIT", clientId = "wit-client", authenticationUrl = "https://login.example")
        val secrets = AssetSecrets(password = "secret", clientSecret = "client-secret")

        val samples = connector.fetch(asset("CONSUMPTION", connection), connection, secrets, Instant.parse("2026-03-01T09:00:00Z"), false)
        assertEquals(2, samples.size)
        assertSame("1500", samples[0].powerW)
        assertTrue(http.requests.any { it.uri().toString() == "https://login.example/token" })
        assertSame("-2000", connector.fetch(asset("PRODUCTION", connection), connection, secrets, null, true).single().powerW)
        assertThrows(AssetConnectionError::class.java) { connector.fetch(asset("CONSUMPTION_AND_PRODUCTION", connection), connection, secrets, null, true) }
        assertThrows(InvalidRequest::class.java) { connector.check(connection.copy(authenticationUrl = null), secrets) }
    }

    @Test
    fun `les adresses des equipements doivent etre publiques et en HTTPS`() {
        val http = AssetHttp(json)
        assertThrows(InvalidRequest::class.java) { http.uri("http://meter.example") }
        assertThrows(InvalidRequest::class.java) { http.uri("https://user:pass@meter.example") }
        assertThrows(InvalidRequest::class.java) { http.uri("https://127.0.0.1/api") }
        assertThrows(InvalidRequest::class.java) { http.uri("https://10.1.2.3/api") }
        assertThrows(InvalidRequest::class.java) { http.uri("https://169.254.169.254/latest") }
        assertThrows(AssetConnectionError::class.java) { http.uri("https://does-not-exist.invalid/api") }
        assertEquals("a=1&b=x+y", http.form(mapOf("a" to "1", "skip" to null, "b" to "x y")))
        var calls = 0
        assertEquals("cached", http.token("key") { calls++; "cached" to 3600 })
        assertEquals("cached", http.token("key") { calls++; "other" to 3600 })
        http.forget("key")
        assertEquals("fresh", http.token("key") { calls++; "fresh" to 3600 })
        assertEquals(2, calls)
    }
}
