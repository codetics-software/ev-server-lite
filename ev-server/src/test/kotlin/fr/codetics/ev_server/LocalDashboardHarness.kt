package fr.codetics.ev_server

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.persistence.*
import jakarta.servlet.http.Cookie
import org.springframework.boot.SpringApplication
import org.springframework.boot.ApplicationContextFactory
import org.springframework.mock.web.MockServletContext
import org.springframework.http.HttpMethod
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.GenericWebApplicationContext
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID

/** Local H2 browser test transport. This class is never included in the production JAR. */
fun main() {
    val application = SpringApplication(EvServerApplication::class.java)
    application.setApplicationContextFactory(ApplicationContextFactory {
        GenericWebApplicationContext().apply {
            servletContext = MockServletContext()
        }
    })
    val context = application.run(
        "--spring.profiles.active=local",
        "--server.port=-1",
        "--app.bootstrap.email=",
        "--spring.main.banner-mode=off",
        "--logging.level.root=WARN"
    )

    context.use {
        val db = context.getBean(Database::class.java)
        createBrowserFixtures(context, db)
        val mvc = MockMvcBuilders.webAppContextSetup(context as WebApplicationContext)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .build()

        println("EV_TEST_READY")

        generateSequence(::readlnOrNull).forEach { line ->
            val input = db.json.readTree(line)
            val id = input.path("id").asText()

            try {
                val builder = MockMvcRequestBuilders.request(
                    HttpMethod.valueOf(input.path("method").asText()),
                    URI(input.path("path").asText())
                )
                input.path("headers").properties().forEach { (name, value) ->
                    if (name.equals("cookie", true)) {
                        val cookies = value.asText().split(';').mapNotNull { pair ->
                            val pieces = pair.trim().split('=', limit = 2)

                            if (pieces.size == 2) {
                                Cookie(pieces[0], pieces[1])
                            } else {
                                null
                            }
                        }
                        builder.cookie(*cookies.toTypedArray())
                    } else {
                        builder.header(name, value.asText())
                    }
                }

                if (input.hasNonNull("body")) {
                    builder.content(input.path("body").asText())
                }

                val response = mvc.perform(builder).andReturn().response
                val headers = response.headerNames.associateWith { name ->
                    response.getHeaders(name).toList()
                }
                val result = mapOf(
                    "id" to id,
                    "status" to response.status,
                    "headers" to headers,
                    "body" to response.contentAsString
                )
                println("EV_TEST_RESPONSE:${db.encode(result)}")
            } catch (_: Exception) {
                val failure = mapOf(
                    "id" to id,
                    "status" to 500,
                    "headers" to emptyMap<String, String>(),
                    "body" to "{\"detail\":\"Local test request failed\"}"
                )
                println("EV_TEST_RESPONSE:${db.encode(failure)}")
            }
        }
    }
}

private fun createBrowserFixtures(context: org.springframework.context.ApplicationContext, db: Database) {
    val tenants = context.getBean(TenantRepository::class.java)
    val users = context.getBean(UserRepository::class.java)
    val stations = context.getBean(StationRepository::class.java)
    val tokens = context.getBean(ChargingTokenRepository::class.java)
    val records = context.getBean(RecordRepository::class.java)
    val passwords = context.getBean(PasswordEncoder::class.java)
    val ocpp = context.getBean(OcppService::class.java)
    val password = randomToken()
    val tenant = tenants.save(TenantEntity(slug = "codetics", name = "Codetics test network"))
    val accounts = listOf("ADMIN", "OPERATOR", "VIEWER", "DRIVER").associateWith { role ->
        users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "${role.lowercase()}@example.test",
                displayName = "${role.lowercase().replaceFirstChar(Char::uppercase)} account",
                passwordHash = passwords.encode(password),
                role = role,
                emailVerified = true
            )
        )
    }
    val driver = requireNotNull(accounts["DRIVER"])
    tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TEST-DRIVER"), driver.id))

    val names = listOf("Paris · Atelier", "Lyon · République", "Bordeaux · Quai", "Nantes · Campus")

    names.forEachIndexed { index, name ->
        val id = "CDT-${1001 + index}"
        stations.save(StationEntity(StationKey(tenant.id, id), name, requireNotNull(passwords.encode(randomToken()))))
        stationCall(
            db, ocpp, tenant.id, id, "BootNotification",
            mapOf(
                "chargePointVendor" to "Codetics Test",
                "chargePointModel" to "AC22"
            )
        )
        val status = if (index == 0) "Charging" else if (index == 3) "Unavailable" else "Available"
        stationCall(
            db, ocpp, tenant.id, id, "StatusNotification",
            mapOf(
                "connectorId" to 1,
                "status" to status,
                "errorCode" to "NoError"
            )
        )

        val startedAt = Instant.now().minusSeconds(3600L * (index + 1))
        val start = stationCall(
            db, ocpp, tenant.id, id, "StartTransaction",
            mapOf(
                "connectorId" to 1,
                "idTag" to "TEST-DRIVER",
                "meterStart" to 100000,
                "timestamp" to startedAt.toString()
            )
        )
        val transactionId = db.json.readTree(start)[2].path("transactionId").intValue()

        if (index > 0) {
            stationCall(
                db, ocpp, tenant.id, id, "StopTransaction",
                mapOf(
                    "transactionId" to transactionId,
                    "meterStop" to 114500 + index * 1000,
                    "timestamp" to startedAt.plusSeconds(1800).toString(),
                    "reason" to "Local"
                )
            )
        }
    }

    records.saveAll(
        listOf(
            RecordEntity(
                key = RecordKey(tenant.id, "assets"),
                name = "Atelier — main energy meter",
                data = db.encode(mapOf("type" to "METER", "maximumPowerKw" to "120"))
            ),
            RecordEntity(
                key = RecordKey(tenant.id, "assets"),
                name = "Campus — solar array",
                data = db.encode(mapOf("type" to "SOLAR", "maximumPowerKw" to "36"))
            ),
            RecordEntity(
                key = RecordKey(tenant.id, "sites"),
                name = "Paris · Atelier",
                data = db.encode(mapOf("address" to "Paris", "country" to "FR"))
            )
        )
    )

    val path = Path.of(".local", "browser-credentials.json")
    Files.createDirectories(path.parent)
    val credentials = mapOf(
        "tenant" to "codetics",
        "password" to password,
        "roles" to accounts.mapValues { entry -> entry.value.email }
    )
    Files.writeString(path, db.encode(credentials))
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
}

private fun stationCall(
    db: Database,
    service: OcppService,
    tenant: UUID,
    station: String,
    action: String,
    payload: Map<String, Any>
): String {
    return service.call(
        tenant,
        station,
        UUID.randomUUID().toString(),
        action,
        db.json.valueToTree(payload)
    )
}
