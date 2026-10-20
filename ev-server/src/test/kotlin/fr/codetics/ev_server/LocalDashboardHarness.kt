package fr.codetics.ev_server

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.models.entities.*
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.RecordEntity
import fr.codetics.ev_server.models.entities.RecordKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.workspaces.WorkspaceComponents
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.repositories.*
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.RecordRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import jakarta.servlet.http.Cookie
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID
import org.springframework.boot.ApplicationContextFactory
import org.springframework.boot.SpringApplication
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockServletContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.GenericWebApplicationContext

/** Transport H2 local pour les essais navigateur, absent du JAR de production. */
fun main() {
    val application =
        SpringApplication(
            EvServerApplication::class.java,
            fr.codetics.ev_server.billing.PaymentTestConfiguration::class.java,
        )
    application.setApplicationContextFactory(
        ApplicationContextFactory {
            GenericWebApplicationContext().apply { servletContext = MockServletContext() }
        }
    )
    val context =
        application.run(
            "--spring.profiles.active=local",
            "--server.port=-1",
            "--app.bootstrap.email=",
            "--app.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "--spring.main.banner-mode=off",
            "--logging.level.root=WARN",
        )

    context.use {
        val db = context.getBean(Database::class.java)
        createBrowserFixtures(context, db)
        val mvc =
            MockMvcBuilders.webAppContextSetup(context as WebApplicationContext)
                .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(
                    springSecurity()
                )
                .build()

        println("EV_TEST_READY")

        generateSequence(::readlnOrNull).forEach { line ->
            val input = db.json.readTree(line)
            val id = input.path("id").asText()

            try {
                val builder =
                    MockMvcRequestBuilders.request(
                        HttpMethod.valueOf(input.path("method").asText()),
                        URI(input.path("path").asText()),
                    )
                input.path("headers").properties().forEach { (name, value) ->
                    if (name.equals("cookie", true)) {
                        val cookies =
                            value.asText().split(';').mapNotNull { pair ->
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
                val headers =
                    response.headerNames.associateWith { name ->
                        response.getHeaders(name).toList()
                    }
                val result =
                    mapOf(
                        "id" to id,
                        "status" to response.status,
                        "headers" to headers,
                        "body" to response.contentAsString,
                    )
                println("EV_TEST_RESPONSE:${db.encode(result)}")
            } catch (failure: Exception) {
                failure.printStackTrace(System.err)
                val failure =
                    mapOf(
                        "id" to id,
                        "status" to 500,
                        "headers" to emptyMap<String, String>(),
                        "body" to "{\"detail\":\"La demande de test locale a échoué.\"}",
                    )
                println("EV_TEST_RESPONSE:${db.encode(failure)}")
            }
        }
    }
}

private fun createBrowserFixtures(
    context: org.springframework.context.ApplicationContext,
    db: Database,
) {
    val tenants = context.getBean(TenantRepository::class.java)
    val users = context.getBean(UserRepository::class.java)
    val stations = context.getBean(StationRepository::class.java)
    val tokens = context.getBean(ChargingTokenRepository::class.java)
    val records = context.getBean(RecordRepository::class.java)
    val passwords = context.getBean(PasswordEncoder::class.java)
    val ocpp = context.getBean(OcppService::class.java)
    val password = randomToken()
    val tenant = tenants.save(TenantEntity(slug = "codetics", name = "Réseau Codetics"))
    val accounts =
        listOf("ADMIN", "OPERATOR", "VIEWER", "DRIVER").associateWith { role ->
            users.save(
                UserEntity(
                    tenantId = tenant.id,
                    email = "${role.lowercase()}@example.test",
                    displayName =
                        when (role) {
                            "ADMIN" -> "Camille Martin"
                            "OPERATOR" -> "Alexandre Petit"
                            "VIEWER" -> "Marie Laurent"
                            else -> "Thomas Bernard"
                        },
                    passwordHash = passwords.encode(password),
                    role = UserRole.valueOf(role),
                    emailVerified = true,
                )
            )
        }
    val driver = requireNotNull(accounts["DRIVER"])
    tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TEST-DRIVER"), driver.id))

    tenant.components =
        db.encode(
            WorkspaceComponents(
                smartCharging = true,
                assets = true,
                billing = true,
            )
        )
    tenants.save(tenant)
    fun record(kind: String, name: String, data: Map<String, Any?>) =
        records.save(RecordEntity(key = RecordKey(tenant.id, kind), name = name, data = db.encode(data))).key.id
    val company = record("companies", "Codetics", mapOf("city" to "Paris", "country" to "FR"))
    val cities =
        listOf(
            Triple("Paris", "48.8566", "2.3522"),
            Triple("Lyon", "45.7640", "4.8357"),
            Triple("Bordeaux", "44.8378", "-0.5792"),
            Triple("Nantes", "47.2184", "-1.5536"),
        )
    val areas =
        cities.map { (city, latitude, longitude) ->
            val site =
                record(
                    "sites",
                    city,
                    mapOf(
                        "companyId" to company.toString(),
                        "address" to "1 place de la Gare",
                        "city" to city,
                        "country" to "FR",
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "public" to (city != "Nantes"),
                    ),
                )
            record(
                "site-areas",
                "$city · Parking",
                mapOf("siteId" to site.toString(), "maximumPowerKw" to "44", "smartCharging" to (city == "Paris")),
            )
        }
    tokens.save(
        ChargingTokenEntity(
            ChargingTokenKey(tenant.id, "VISITOR-01"),
            visualId = "0042",
            description = "Badge visiteur",
        )
    )

    val names = listOf("Paris · Atelier", "Lyon · République", "Bordeaux · Quai", "Nantes · Campus")

    names.forEachIndexed { index, name ->
        val id = "CDT-${1001 + index}"
        stations.save(
            StationEntity(
                StationKey(tenant.id, id),
                name,
                requireNotNull(passwords.encode(randomToken())),
                connected = index != 3,
                siteAreaId = areas[index],
                maximumPowerKw = BigDecimal(22),
            )
        )
        stationCall(
            db,
            ocpp,
            tenant.id,
            id,
            "BootNotification",
            mapOf("chargePointVendor" to "Codetics", "chargePointModel" to "AC22"),
        )
        val status = if (index == 0) "Charging" else if (index == 3) "Unavailable" else "Available"
        stationCall(
            db,
            ocpp,
            tenant.id,
            id,
            "StatusNotification",
            mapOf("connectorId" to 1, "status" to status, "errorCode" to "NoError"),
        )

        val startedAt = Instant.now().minusSeconds(3600L * (index + 1))
        val start =
            stationCall(
                db,
                ocpp,
                tenant.id,
                id,
                "StartTransaction",
                mapOf(
                    "connectorId" to 1,
                    "idTag" to "TEST-DRIVER",
                    "meterStart" to 100000,
                    "timestamp" to startedAt.toString(),
                ),
            )
        val transactionId = db.json.readTree(start)[2].path("transactionId").intValue()

        stationCall(
            db,
            ocpp,
            tenant.id,
            id,
            "MeterValues",
            mapOf(
                "connectorId" to 1,
                "transactionId" to transactionId,
                "meterValue" to
                    (1..6).map { step ->
                        mapOf(
                            "timestamp" to startedAt.plusSeconds(step * 300L).toString(),
                            "sampledValue" to
                                listOf(
                                    mapOf("value" to "${100000 + step * 1800}", "measurand" to "Energy.Active.Import.Register", "unit" to "Wh"),
                                    mapOf("value" to "${21 - step}", "measurand" to "Power.Active.Import", "unit" to "kW"),
                                    mapOf("value" to "${30 + step * 5}", "measurand" to "SoC", "unit" to "Percent"),
                                ),
                        )
                    },
            ),
        )

        if (index > 0) {
            stationCall(
                db,
                ocpp,
                tenant.id,
                id,
                "StopTransaction",
                mapOf(
                    "transactionId" to transactionId,
                    "meterStop" to 114500 + index * 1000,
                    "timestamp" to startedAt.plusSeconds(1800).toString(),
                    "reason" to "Local",
                ),
            )
        }
    }

    record(
        "assets",
        "Atelier — compteur principal",
        mapOf("siteAreaId" to areas[0].toString(), "type" to "CONSUMPTION", "kind" to "BUILDING", "maximumPowerKw" to "120", "staticPowerKw" to "8"),
    )
    record(
        "assets",
        "Campus — production solaire",
        mapOf("siteAreaId" to areas[3].toString(), "type" to "PRODUCTION", "kind" to "SOLAR", "maximumPowerKw" to "36"),
    )
    record(
        "charging-station-templates",
        "Codetics AC22",
        mapOf(
            "vendor" to "Codetics",
            "model" to "AC22",
            "maximumPowerKw" to "22",
            "connectors" to listOf(mapOf("connectorId" to 1, "type" to "Type2", "maximumPowerKw" to "22", "phases" to 3)),
            "parameters" to listOf(mapOf("key" to "HeartbeatInterval", "value" to "60")),
        ),
    )
    record(
        "pricing-definitions",
        "Heures creuses",
        mapOf(
            "currency" to "EUR",
            "energyPrice" to "0.25",
            "restrictions" to mapOf("timeFrom" to "22:00", "timeTo" to "06:00"),
            "priority" to 10,
        ),
    )

    val tariff =
        records.save(
            RecordEntity(
                RecordKey(tenant.id, "pricing-definitions"),
                "Recharge visiteurs",
                data =
                    db.encode(
                        mapOf(
                            "currency" to "EUR",
                            "energyPrice" to "0.39",
                            "timePrice" to "0",
                            "flatPrice" to "0.50",
                        )
                    ),
            )
        )
    context
        .getBean(WorkspacePaymentSettingsRepository::class.java)
        .save(
            WorkspacePaymentSettings(
                tenantId = tenant.id,
                stripeAccountId = "acct_demo",
                enabled = true,
            )
        )
    val evse =
        context
            .getBean(PublicEvseRepository::class.java)
            .save(
                PublicEvseEntity(
                    tenantId = tenant.id,
                    stationId = "CDT-1002",
                    connectorId = 1,
                    name = "Lyon · Parking visiteurs",
                    pricingDefinitionId = tariff.key.id,
                    published = true,
                )
            )

    val path = Path.of(".local", "browser-credentials.json")
    Files.createDirectories(path.parent)
    val credentials =
        mapOf(
            "tenant" to "codetics",
            "publicEvse" to evse.id.toString(),
            "password" to password,
            "roles" to accounts.mapValues { entry -> entry.value.email },
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
    payload: Map<String, Any>,
): String {
    return service.call(
        tenant,
        station,
        UUID.randomUUID().toString(),
        action,
        db.json.valueToTree(payload),
    )
}
