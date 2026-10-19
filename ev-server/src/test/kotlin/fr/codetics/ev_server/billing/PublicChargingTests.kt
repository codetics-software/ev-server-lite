package fr.codetics.ev_server.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.billing.CheckoutRequest
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.*
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.RecordEntity
import fr.codetics.ev_server.models.entities.RecordKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import fr.codetics.ev_server.models.evses.EvseInput
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.repositories.*
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.RecordRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.services.billing.*
import fr.codetics.ev_server.services.billing.ChargingCheckoutService
import fr.codetics.ev_server.services.billing.ChargingPaymentWorker
import fr.codetics.ev_server.services.billing.PortalSessionService
import fr.codetics.ev_server.services.billing.StripeWebhookService
import fr.codetics.ev_server.services.evses.EvseService
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.server.ResponseStatusException

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
class PublicChargingTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val records: RecordRepository,
    private val settings: WorkspacePaymentSettingsRepository,
    private val payments: ChargingPaymentRepository,
    private val commands: CommandRepository,
    private val evses: EvseService,
    private val checkout: ChargingCheckoutService,
    private val portal: PortalSessionService,
    private val webhook: StripeWebhookService,
    private val worker: ChargingPaymentWorker,
    private val ocpp: OcppService,
    private val gateway: TestPaymentGateway,
) {
    private lateinit var tenant: TenantEntity
    private lateinit var evse: UUID
    private lateinit var mvc: MockMvc
    private val access = "a".repeat(64)
    private val startTime = Instant.now().minusSeconds(60)

    @BeforeEach
    fun prepare() {
        mvc =
            MockMvcBuilders.webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        tenant =
            tenants.save(
                TenantEntity(slug = "paiement-${UUID.randomUUID()}", name = "Réseau de test")
            )
        stations.save(
            StationEntity(
                key = StationKey(tenant.id, "BORNE-01"),
                name = "Borne de test",
                secretHash = "test",
                connected = true,
            )
        )
        connectors.save(
            ConnectorEntity(ConnectorKey(tenant.id, "BORNE-01", 1), status = "Available")
        )
        val tariff =
            records.save(
                RecordEntity(
                    RecordKey(tenant.id, "pricing-definitions", UUID.randomUUID()),
                    "Tarif public",
                    data =
                        db.encode(
                            mapOf(
                                "currency" to "EUR",
                                "energyPrice" to "0.50",
                                "timePrice" to "0",
                                "flatPrice" to "0",
                            )
                        ),
                )
            )
        settings.save(
            WorkspacePaymentSettings(
                tenantId = tenant.id,
                stripeAccountId = "acct_${tenant.id}",
                enabled = true,
            )
        )
        val admin =
            AppPrincipal(
                UUID.randomUUID(),
                tenant.id,
                "admin@example.test",
                "Administrateur",
                "ADMIN",
            )
        evse =
            evses.save(admin, EvseInput("Parking visiteurs", "BORNE-01", 1, tariff.key.id, true)).id
    }

    private fun create(credit: Long = 2000, requestId: UUID = UUID.randomUUID()) =
        checkout.create(tenant.slug, evse, CheckoutRequest(requestId, access, credit))

    private fun event(
        id: UUID,
        eventId: String = "evt_${UUID.randomUUID()}",
        account: String = "acct_${tenant.id}",
        amount: Long? = null,
        type: String = "checkout.session.completed",
        paymentStatus: String = "paid",
    ): String {
        val order = payments.findById(id).orElseThrow()
        return db.encode(
            mapOf(
                "id" to eventId,
                "account" to account,
                "type" to type,
                "data" to
                    mapOf(
                        "id" to order.stripeCheckoutId,
                        "metadata" to mapOf("charging_order_id" to id),
                        "payment_status" to paymentStatus,
                        "amount_total" to (amount ?: order.prepaidMinor),
                        "currency" to "eur",
                        "payment_intent" to "pi_$id",
                    ),
            )
        )
    }

    private fun pay(id: UUID) = webhook.receive(event(id), "signature-de-test")

    private fun call(action: String, payload: Any, station: String = "BORNE-01") =
        db.decode(
            ocpp.call(
                tenant.id,
                station,
                UUID.randomUUID().toString(),
                action,
                db.json.valueToTree(payload),
            )
        )

    private fun start(
        id: UUID,
        station: String = "BORNE-01",
        connector: Int = 1,
    ): tools.jackson.databind.JsonNode {
        val order = payments.findById(id).orElseThrow()
        return call(
            "StartTransaction",
            mapOf(
                "connectorId" to connector,
                "idTag" to order.tokenUid,
                "meterStart" to 1000,
                "timestamp" to startTime.toString(),
            ),
            station,
        )[2]
    }

    @Test
    fun `le portail et le QR utilisent un espace explicite sans nom de domaine`() {
        assertTrue(evses.portalUrl(tenant.id, evse).endsWith("/charge/${tenant.slug}/$evse"))
        assertThrows(ResponseStatusException::class.java) { evses.portal("autre-espace", evse) }
        assertThrows(ResponseStatusException::class.java) { evses.find(UUID.randomUUID(), evse) }
        mvc.perform(get("/api/v1/public/evses/${tenant.slug}/$evse")).andExpect(status().isOk)
    }

    @Test
    fun `la reprise du paiement conserve une seule commande et un seul encaissement`() {
        val request = UUID.randomUUID()
        val first = create(requestId = request)
        assertEquals(first, create(requestId = request))
        val notification = event(first.orderId)
        webhook.receive(notification, "signature-de-test")
        webhook.receive(notification, "signature-de-test")
        pay(first.orderId)
        val order = payments.findById(first.orderId).orElseThrow()
        assertEquals(PaymentState.START_REQUESTED, order.state)
        assertNull(order.transactionId)
        assertEquals(
            1,
            commands.findAll().count {
                it.tenantId == tenant.id && it.action == "RemoteStartTransaction"
            },
        )
        assertThrows(ResponseStatusException::class.java) {
            portal.read(first.orderId, "b".repeat(64))
        }
        assertThrows(ResponseStatusException::class.java) { create() }
    }

    @Test
    fun `le compte le montant et la signature sont controles avant le demarrage`() {
        val id = create().orderId
        assertThrows(IllegalArgumentException::class.java) {
            webhook.receive(event(id), "fausse-signature")
        }
        assertThrows(ResponseStatusException::class.java) {
            webhook.receive(event(id, account = "acct_autre"), "signature-de-test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            webhook.receive(event(id, amount = 1), "signature-de-test")
        }
        assertEquals(PaymentState.PENDING_PAYMENT, payments.findById(id).orElseThrow().state)
        mvc.perform(
                post("/api/v1/stripe/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", "fausse-signature")
                    .content(event(id))
            )
            .andExpect(status().isBadRequest)
        mvc.perform(
                post("/api/v1/stripe/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", "signature-de-test")
                    .content(event(id))
            )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `un paiement asynchrone attend la confirmation payee`() {
        val id = create().orderId
        webhook.receive(event(id, paymentStatus = "unpaid"), "signature-de-test")
        assertEquals(PaymentState.PENDING_PAYMENT, payments.findById(id).orElseThrow().state)
        webhook.receive(
            event(id, type = "checkout.session.async_payment_succeeded"),
            "signature-de-test",
        )
        assertEquals(PaymentState.START_REQUESTED, payments.findById(id).orElseThrow().state)
    }

    @Test
    fun `le credit inutilise est rembourse apres le releve final uniquement`() {
        val id = create().orderId
        pay(id)
        val transaction = start(id).path("transactionId").intValue()
        assertEquals(PaymentState.CHARGING, payments.findById(id).orElseThrow().state)
        portal.stop(id, access)
        worker.advance(id)
        assertFalse(gateway.refunds.containsKey(id))
        call(
            "StopTransaction",
            mapOf(
                "transactionId" to transaction,
                "meterStop" to 5000,
                "timestamp" to Instant.now().toString(),
            ),
        )
        worker.advance(id)
        worker.advance(id)
        val settled = portal.read(id, access)
        assertEquals(PaymentState.COMPLETED, settled.state)
        assertEquals(200L, settled.billedMinor)
        assertEquals(1800L, settled.refundedMinor)
        assertEquals(1800L, gateway.refunds[id])
    }

    @Test
    fun `la consommation declenche un arret sans facturer au dela du credit`() {
        val id = create(500).orderId
        pay(id)
        val transaction = start(id).path("transactionId").intValue()
        call(
            "MeterValues",
            mapOf(
                "connectorId" to 1,
                "transactionId" to transaction,
                "meterValue" to
                    listOf(
                        mapOf(
                            "timestamp" to Instant.now().toString(),
                            "sampledValue" to listOf(mapOf("value" to "12000", "unit" to "Wh")),
                        )
                    ),
            ),
        )
        assertEquals(PaymentState.STOP_REQUESTED, payments.findById(id).orElseThrow().state)
        call(
            "StopTransaction",
            mapOf(
                "transactionId" to transaction,
                "meterStop" to 12000,
                "timestamp" to Instant.now().toString(),
            ),
        )
        worker.advance(id)
        assertEquals(500L, portal.read(id, access).billedMinor)
        assertEquals(0L, portal.read(id, access).refundedMinor)
    }

    @Test
    fun `un badge public ne permet pas de demarrer un autre connecteur`() {
        val id = create().orderId
        pay(id)
        val result = start(id, connector = 2)
        assertEquals("Blocked", result.path("idTagInfo").path("status").asText())
        assertEquals(PaymentState.START_REQUESTED, payments.findById(id).orElseThrow().state)
        assertNull(payments.findById(id).orElseThrow().transactionId)
    }

    @Test
    fun `une borne qui refuse le demarrage entraine le remboursement integral`() {
        val id = create().orderId
        pay(id)
        val order = payments.findById(id).orElseThrow()
        val command = commands.findById(requireNotNull(order.startCommandId)).orElseThrow()
        command.state = "SUCCEEDED"
        command.result = "{\"status\":\"Rejected\"}"
        commands.save(command)
        worker.advance(id)
        assertEquals(2000L, portal.read(id, access).refundedMinor)
        val late = start(id)
        assertEquals("Blocked", late.path("idTagInfo").path("status").asText())
        assertEquals(PaymentState.COMPLETED, payments.findById(id).orElseThrow().state)
    }

    @Test
    fun `la creation publique exige la protection CSRF et une cle de suivi valide`() {
        val path = "/api/v1/public/evses/${tenant.slug}/$evse/checkout"
        val body = db.encode(CheckoutRequest(UUID.randomUUID(), access, 2000))
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
        assertThrows(IllegalArgumentException::class.java) {
            checkout.create(tenant.slug, evse, CheckoutRequest(UUID.randomUUID(), "court", 2000))
        }
    }
}
