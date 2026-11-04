// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.customer

import fr.codetics.ev_server.billing.PaymentTestConfiguration
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.repositories.CardOrderRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.services.billing.StripeWebhookService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@ActiveProfiles("local")
@Import(PaymentTestConfiguration::class)
@SpringBootTest(
    classes = [fr.codetics.ev_server.EvServerApplication::class],
    properties =
        [
            "app.bootstrap.email=",
            "app.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "app.payments.initial-delay-milliseconds=3600000",
        ],
)
class AppContractFixtureTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val tokens: ChargingTokenRepository,
    private val settings: WorkspacePaymentSettingsRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
    private val cardOrders: CardOrderRepository,
    private val webhook: StripeWebhookService,
) {
    @Test
    fun `les reponses du parcours client sont enregistrees pour l application`() {
        val mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        val directory = Path.of("target/app-fixtures").also(Files::createDirectories)
        val tenant = tenants.save(TenantEntity(slug = "contrat-${UUID.randomUUID()}", name = "Réseau Contrat"))
        fun account(role: UserRole) =
            users.save(
                UserEntity(
                    tenantId = tenant.id,
                    email = "${role.name.lowercase()}-${UUID.randomUUID()}@example.test",
                    displayName = "${role.name} contrat",
                    passwordHash = passwords.encode("a-long-password-for-testing"),
                    role = role,
                    emailVerified = true,
                )
            )
        val admin = account(UserRole.ADMIN)
        val driver = account(UserRole.DRIVER)
        fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
            with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")
        fun fetch(builder: MockHttpServletRequestBuilder): String =
            mvc.perform(builder).andExpect(status().isOk).andReturn().response.contentAsString
        fun save(name: String, builder: MockHttpServletRequestBuilder): String =
            fetch(builder).also { Files.writeString(directory.resolve("$name.json"), it) }
        fun jsonBody(builder: MockHttpServletRequestBuilder, body: Any) =
            builder.contentType(MediaType.APPLICATION_JSON).content(db.encode(body))
        fun record(kind: String, name: String, data: Map<String, Any?>) =
            db.decode(fetch(jsonBody(post("/api/v1/records/$kind").as_(admin), mapOf("name" to name, "data" to data)))).path("id").asText()

        val company = record("companies", "Codetics", mapOf("country" to "FR"))
        val site =
            record(
                "sites",
                "Gare",
                mapOf("companyId" to company, "address" to "1 place de la Gare", "city" to "Lyon", "postalCode" to "69002", "country" to "FR", "latitude" to 45.76, "longitude" to 4.83, "public" to true),
            )
        val area = record("site-areas", "Parking", mapOf("siteId" to site))
        val tariff = record("pricing-definitions", "Tarif", mapOf("currency" to "EUR", "energyPrice" to 0.39, "timePrice" to 1.2, "flatPrice" to 0.5))
        record("car-catalog", "Renault Zoé", mapOf("make" to "Renault", "model" to "Zoé", "version" to "R135", "batteryKwh" to 52, "maxAcPowerKw" to 22, "maxDcPowerKw" to 50))
        mvc.perform(jsonBody(put("/api/v1/sites/$site/users").as_(admin), listOf(mapOf("userId" to driver.id.toString())))).andExpect(status().isOk)
        stations.save(StationEntity(StationKey(tenant.id, "CONTRAT-1"), "Borne Contrat", "hash", connected = true, siteAreaId = UUID.fromString(area), publicStation = true, vendor = "Codetics", model = "Lite"))
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "04A1B2C3D4E580"), driver.id, visualId = "CARTE-1", defaultToken = true))
        fun call(action: String, payload: Map<String, Any?>) =
            db.decode(ocpp.call(tenant.id, "CONTRAT-1", UUID.randomUUID().toString(), action, db.json.valueToTree(payload)))[2]
        call("StatusNotification", mapOf("connectorId" to 1, "status" to "Available", "errorCode" to "NoError"))
        val evse =
            db.decode(
                fetch(
                    jsonBody(
                        post("/api/v1/evses").as_(admin),
                        mapOf("name" to "Point QR", "stationId" to "CONTRAT-1", "connectorId" to 1, "pricingDefinitionId" to tariff, "published" to true),
                    )
                )
            ).path("id").asText()
        settings.save(
            WorkspacePaymentSettings(tenantId = tenant.id).apply {
                enabled = true
                stripeAccountId = "acct_contrat"
            }
        )

        mvc.perform(
            jsonBody(put("/api/v1/card-orders/settings").as_(admin), mapOf("enabled" to true, "priceMinor" to 1500, "description" to "Carte RFID", "shipping" to true))
        ).andExpect(status().isOk)
        val cardOrder =
            db.decode(
                fetch(
                    jsonBody(
                        post("/api/v1/account/card-orders").as_(driver),
                        mapOf("requestId" to UUID.randomUUID().toString(), "recipientName" to "Thomas", "address" to "1 rue", "postalCode" to "69002", "city" to "Lyon"),
                    )
                )
            ).path("order_id").asText()
        save("card_offer", get("/api/v1/account/card-offer").as_(driver))
        save("card_orders", get("/api/v1/account/card-orders").as_(driver))
        val stored = cardOrders.findById(UUID.fromString(cardOrder)).orElseThrow()
        webhook.receive(
            db.encode(
                mapOf(
                    "id" to "evt_${UUID.randomUUID()}",
                    "account" to stored.stripeAccountId,
                    "type" to "checkout.session.completed",
                    "data" to
                        mapOf(
                            "id" to stored.stripeCheckoutId,
                            "metadata" to mapOf("card_order_id" to cardOrder),
                            "payment_status" to "paid",
                            "amount_total" to stored.amountMinor,
                            "currency" to "eur",
                            "payment_intent" to "pi_$cardOrder",
                        ),
                )
            ),
            "signature-de-test",
        )
        fetch(jsonBody(post("/api/v1/card-orders/$cardOrder/assign").as_(admin), mapOf("uid" to "04AABBCCDDEE80", "visualId" to "CARTE-2")))
        save("card_activated", jsonBody(post("/api/v1/account/card-orders/activate").as_(driver), mapOf("number" to "CARTE-2")))

        save("configuration", get("/api/v1/public/mobile/configuration"))
        save("csrf", get("/api/v1/auth/csrf"))
        save("eula", get("/api/v1/auth/eula"))
        save(
            "signin",
            jsonBody(post("/api/v1/auth/app/signin").with(csrf()), mapOf("tenant" to tenant.slug, "email" to driver.email, "password" to "a-long-password-for-testing")),
        )
        save("workspace", get("/api/v1/workspace").as_(driver))
        save("account", get("/api/v1/account").as_(driver))
        save("sites", get("/api/v1/account/sites").as_(driver))
        save("tokens", get("/api/v1/account/tokens").as_(driver))
        save("public_stations", get("/api/v1/public/workspaces/${tenant.slug}/stations"))
        save("portal", get("/api/v1/public/evses/${tenant.slug}/$evse"))

        val access = randomToken().take(1).let { "ab".repeat(32) }
        val order =
            db.decode(
                fetch(
                    jsonBody(
                        post("/api/v1/public/evses/${tenant.slug}/$evse/checkout").with(csrf()),
                        mapOf("requestId" to UUID.randomUUID().toString(), "accessToken" to access, "creditMinor" to 2000),
                    )
                )
            ).path("orderId").asText()
        save("guest", get("/api/v1/public/sessions/$order").header("X-Charge-Token", access))
        fetch(jsonBody(post("/api/v1/public/sessions/$order/stop").with(csrf()).header("X-Charge-Token", access), emptyMap<String, Any>()))

        mvc.perform(
            jsonBody(post("/api/v1/transactions/start").as_(driver), mapOf("stationId" to "CONTRAT-1", "connectorId" to 1, "tokenUid" to "04A1B2C3D4E580"))
        ).andExpect(status().isOk)
        save("receipt", jsonBody(post("/api/v1/transactions/start").as_(driver), mapOf("stationId" to "CONTRAT-1", "connectorId" to 1, "tokenUid" to "04A1B2C3D4E580")))
        val started = call("StartTransaction", mapOf("connectorId" to 1, "idTag" to "04A1B2C3D4E580", "meterStart" to 1000, "timestamp" to Instant.now().toString()))
        val id = started.path("transactionId").asInt()
        call(
            "MeterValues",
            mapOf(
                "connectorId" to 1,
                "transactionId" to id,
                "meterValue" to
                    listOf(
                        mapOf(
                            "timestamp" to Instant.now().toString(),
                            "sampledValue" to
                                listOf(
                                    mapOf("value" to "4500", "measurand" to "Energy.Active.Import.Register", "unit" to "Wh"),
                                    mapOf("value" to "11000", "measurand" to "Power.Active.Import", "unit" to "W"),
                                    mapOf("value" to "54", "measurand" to "SoC", "unit" to "Percent"),
                                ),
                        )
                    ),
            ),
        )
        save("stations", get("/api/v1/account/stations").as_(driver))
        save("station", get("/api/v1/account/stations/CONTRAT-1").as_(driver))
        save("session", get("/api/v1/transactions/$id").as_(driver))
        save("sessions", get("/api/v1/transactions?status=active").as_(driver))
        save("consumptions", get("/api/v1/transactions/$id/consumptions").as_(driver))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1)
        fetch(post("/api/v1/account/stations/CONTRAT-1/photos").as_(driver).contentType(MediaType.IMAGE_JPEG).content(jpeg))
        save("photos", get("/api/v1/account/stations/CONTRAT-1/photos").as_(driver))
        fetch(
            jsonBody(
                post("/api/v1/account/cars").as_(driver),
                mapOf("name" to "Zoé", "data" to mapOf("make" to "Renault", "model" to "Zoé", "licensePlate" to "AA-123-BB", "batteryKwh" to 52, "default" to true)),
            )
        )
        save("cars", get("/api/v1/account/cars").as_(driver))
        save("catalog", get("/api/v1/account/car-catalog").as_(driver))
        save("autocharge", get("/api/v1/account/autocharge/candidates").as_(driver))
        save("notifications", get("/api/v1/notifications").as_(driver))
        save("types", get("/api/v1/notifications/types").as_(driver))
        save("preferences", get("/api/v1/account/notifications").as_(driver))
        save("invoices", get("/api/v1/invoices").as_(driver))
        call("StopTransaction", mapOf("transactionId" to id, "meterStop" to 9000, "timestamp" to Instant.now().toString(), "idTag" to "04A1B2C3D4E580"))
        save("statistics", get("/api/v1/statistics/consumption").as_(driver))
    }
}
