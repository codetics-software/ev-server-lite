// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.customer

import fr.codetics.ev_server.billing.PaymentTestConfiguration
import fr.codetics.ev_server.billing.TestPaymentGateway
import fr.codetics.ev_server.core.Database
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
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
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
class CardOrderTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val tokens: ChargingTokenRepository,
    private val stations: StationRepository,
    private val settings: WorkspacePaymentSettingsRepository,
    private val orders: CardOrderRepository,
    private val accounts: AccountService,
    private val webhook: StripeWebhookService,
    private val gateway: TestPaymentGateway,
    private val ocpp: OcppService,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private lateinit var driver: UserEntity
    private lateinit var other: UserEntity

    private val address =
        mapOf("recipientName" to "Thomas Bernard", "address" to "1 rue de Paris", "postalCode" to "75001", "city" to "Paris")

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "carte-${UUID.randomUUID()}", name = "Réseau Carte"))
        admin = account(UserRole.ADMIN)
        driver = account(UserRole.DRIVER)
        other = account(UserRole.DRIVER)
        settings.save(WorkspacePaymentSettings(tenantId = tenant.id, stripeAccountId = "acct_${tenant.id}", enabled = true))
    }

    private fun account(role: UserRole) =
        users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "${role.name.lowercase()}-${UUID.randomUUID()}@example.test",
                displayName = "${role.name} carte",
                role = role,
                emailVerified = true,
            )
        )

    private fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
        with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")

    private fun send(builder: MockHttpServletRequestBuilder, user: UserEntity, body: Any): ResultActions =
        mvc.perform(builder.as_(user).contentType(MediaType.APPLICATION_JSON).content(db.encode(body)))

    private fun json(result: ResultActions) = db.decode(result.andExpect(status().isOk).andReturn().response.contentAsString)

    private fun enable(shipping: Boolean = true) =
        send(put("/api/v1/card-orders/settings"), admin, mapOf("enabled" to true, "priceMinor" to 1500, "description" to "Carte RFID", "shipping" to shipping))
            .andExpect(status().isOk)

    private fun order(user: UserEntity = driver, requestId: UUID = UUID.randomUUID()): UUID {
        val created = json(send(post("/api/v1/account/card-orders"), user, address + ("requestId" to requestId.toString())))
        assertTrue(created.path("checkout_url").asText().startsWith("https://"))
        return UUID.fromString(created.path("order_id").asText())
    }

    private fun event(id: UUID, type: String = "checkout.session.completed", amount: Long = 1500): String {
        val order = orders.findById(id).orElseThrow()
        return db.encode(
            mapOf(
                "id" to "evt_${UUID.randomUUID()}",
                "account" to order.stripeAccountId,
                "type" to type,
                "data" to
                    mapOf(
                        "id" to order.stripeCheckoutId,
                        "metadata" to mapOf("card_order_id" to id),
                        "payment_status" to "paid",
                        "amount_total" to amount,
                        "currency" to "eur",
                        "payment_intent" to "pi_$id",
                    ),
            )
        )
    }

    private fun pay(id: UUID) = webhook.receive(event(id), "signature-de-test")

    private fun assign(id: UUID, uid: String, visual: String? = null) =
        send(post("/api/v1/card-orders/$id/assign"), admin, mapOf("uid" to uid, "visualId" to visual))

    private fun activate(number: String, user: UserEntity = driver) =
        send(post("/api/v1/account/card-orders/activate"), user, mapOf("number" to number))

    @Test
    fun `une carte achetee puis preparee s active par son numero imprime`() {
        enable()
        val offer = json(mvc.perform(get("/api/v1/account/card-offer").as_(driver)))
        assertTrue(offer.path("available").asBoolean())
        assertEquals(1500, offer.path("price_minor").asInt())

        val id = order()
        assertEquals("PENDING_PAYMENT", orders.findById(id).orElseThrow().state)
        activate("CARTE-77").andExpect(status().isNotFound)
        pay(id)
        assertEquals("PAID", orders.findById(id).orElseThrow().state)
        activate("CARTE-77").andExpect(status().isNotFound)

        assign(id, "04A1B2C3D4E580", "CARTE-77").andExpect(status().isOk)
        val reserved = tokens.findById(ChargingTokenKey(tenant.id, "04A1B2C3D4E580")).orElseThrow()
        assertFalse(reserved.enabled)
        assertNull(reserved.userId)

        val activated = json(activate(" carte 77 "))
        assertEquals("04A1B2C3D4E580", activated.path("uid").asText())
        assertEquals(driver.id.toString(), activated.path("user_id").asText())
        assertTrue(activated.path("enabled").asBoolean())
        assertTrue(activated.path("default").asBoolean())
        assertEquals("ACTIVATED", orders.findById(id).orElseThrow().state)
        assertEquals(1, json(mvc.perform(get("/api/v1/account/tokens").as_(driver))).size())
        activate("CARTE-77").andExpect(status().isNotFound)
    }

    @Test
    fun `une carte s active par le NFC dans les deux sens de lecture`() {
        enable()
        val first = order().also(::pay)
        assign(first, "04A1B2C3D4E581").andExpect(status().isOk)
        json(activate("04:a1:b2:c3:d4:e5:81"))

        val second = order().also(::pay)
        assign(second, "04A1B2C3D4E582").andExpect(status().isOk)
        assertFalse(json(activate("82E5D4C3B2A104")).path("default").asBoolean())
    }

    @Test
    fun `le numero d une carte commandee par un autre ne permet pas de l activer`() {
        enable()
        val id = order().also(::pay)
        assign(id, "04A1B2C3D4E583", "CARTE-83").andExpect(status().isOk)
        order(other).also(::pay)

        activate("CARTE-83", other).andExpect(status().isNotFound)
        activate("04A1B2C3D4E583", other).andExpect(status().isNotFound)
        assertNull(tokens.findById(ChargingTokenKey(tenant.id, "04A1B2C3D4E583")).orElseThrow().userId)
    }

    @Test
    fun `une carte en attente d activation est refusee par la borne`() {
        enable()
        stations.save(StationEntity(StationKey(tenant.id, "CS-1"), "Borne", "hash", connected = true))
        val id = order().also(::pay)
        assign(id, "04A1B2C3D4E584").andExpect(status().isOk)
        fun authorize() =
            db.decode(ocpp.call(tenant.id, "CS-1", UUID.randomUUID().toString(), "Authorize", db.json.valueToTree(mapOf("idTag" to "04A1B2C3D4E584"))))[2]
                .path("idTagInfo").path("status").asText()
        assertEquals("Blocked", authorize())
        json(activate("04A1B2C3D4E584"))
        assertEquals("Accepted", authorize())
    }

    @Test
    fun `une carte deja attribuee ne peut pas etre associee a une commande`() {
        enable()
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "PRISE"), other.id))
        val id = order().also(::pay)
        assign(id, "PRISE").andExpect(status().isConflict)
        val unpaid = order()
        assign(unpaid, "AUTRE").andExpect(status().isBadRequest)
        send(post("/api/v1/card-orders/$id/assign"), driver, mapOf("uid" to "AUTRE")).andExpect(status().isForbidden)
    }

    @Test
    fun `une commande ne se cree que si la vente est active et reste idempotente`() {
        send(post("/api/v1/account/card-orders"), driver, address + ("requestId" to UUID.randomUUID().toString())).andExpect(status().isConflict)
        assertFalse(json(mvc.perform(get("/api/v1/account/card-offer").as_(driver))).path("available").asBoolean())
        send(put("/api/v1/card-orders/settings"), driver, mapOf("enabled" to true, "priceMinor" to 1500)).andExpect(status().isForbidden)

        enable()
        send(post("/api/v1/account/card-orders"), driver, mapOf("requestId" to UUID.randomUUID().toString())).andExpect(status().isBadRequest)
        val request = UUID.randomUUID()
        assertEquals(order(requestId = request), order(requestId = request))
        send(post("/api/v1/account/card-orders"), other, address + ("requestId" to request.toString())).andExpect(status().isBadRequest)
        assertEquals(1, json(mvc.perform(get("/api/v1/account/card-orders").as_(driver))).size())
        assertEquals(0, json(mvc.perform(get("/api/v1/account/card-orders").as_(other))).size())
        mvc.perform(get("/api/v1/card-orders").as_(driver)).andExpect(status().isForbidden)
    }

    @Test
    fun `le webhook verifie le montant et l annulation rembourse`() {
        enable()
        val id = order()
        org.junit.jupiter.api.assertThrows<RuntimeException> { webhook.receive(event(id, amount = 1), "signature-de-test") }
        assertEquals("PENDING_PAYMENT", orders.findById(id).orElseThrow().state)
        pay(id)

        val staff = json(mvc.perform(get("/api/v1/card-orders?state=PAID").as_(admin))).path("items")[0]
        assertEquals("1 rue de Paris", staff.path("address").asText())
        assertEquals(driver.email, staff.path("user_email").asText())

        assertTrue(json(send(post("/api/v1/card-orders/$id/cancel"), admin, emptyMap<String, Any>())).path("refunded").asBoolean())
        assertEquals(1500, gateway.refunds[id])
        assertEquals("CANCELLED", orders.findById(id).orElseThrow().state)
        assign(id, "TROPTARD").andExpect(status().isBadRequest)

        val expired = order()
        webhook.receive(event(expired, type = "checkout.session.expired"), "signature-de-test")
        assertEquals("CANCELLED", orders.findById(expired).orElseThrow().state)
    }

    @Test
    fun `la suppression du compte efface l adresse de livraison`() {
        enable()
        val id = order().also(::pay)
        val bearer = accounts.issue(requireNotNull(accounts.findUser(driver.id)))
        mvc.perform(delete("/api/v1/account").with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")).andExpect(status().isOk)
        val stored = orders.findById(id).orElseThrow()
        assertNull(stored.address)
        assertNull(stored.recipientName)
        assertTrue(stored.createdAt.isBefore(Instant.now().plusSeconds(1)))
    }
}
