// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.customer

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.PushDeviceEntity
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.notifications.PushDeviceInput
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.PushDeviceRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.notifications.PushService
import fr.codetics.ev_server.services.tokens.GoogleWalletPassService
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

private val googlePair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

@ActiveProfiles("local")
@SpringBootTest(properties = ["app.bootstrap.email=", "app.mail-enabled=false"])
class GoogleWalletPassTests
@Autowired
constructor(
    private val service: GoogleWalletPassService,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val tokens: ChargingTokenRepository,
    private val push: PushService,
    private val devices: PushDeviceRepository,
    private val db: Database,
    private val mapper: ObjectMapper,
) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val account =
                JsonMapper.builder()
                    .build()
                    .writeValueAsString(
                        mapOf(
                            "project_id" to "ev-demo",
                            "client_email" to "wallet@ev-demo.iam.gserviceaccount.com",
                            "private_key" to
                                "-----BEGIN PRIVATE KEY-----\n" +
                                    Base64.getEncoder().encodeToString(googlePair.private.encoded) +
                                    "\n-----END PRIVATE KEY-----\n",
                        )
                    )
            registry.add("app.google-wallet.issuer-id") { "3388000000000000000" }
            registry.add("app.google-wallet.service-account") { account }
            registry.add("app.google-wallet.smart-tap-redemption-issuer") { "3388000000000000001" }
            registry.add("app.push.fcm.service-account") { account }
        }
    }

    private fun principal(tenant: TenantEntity, user: UserEntity) =
        AppPrincipal(user.id, tenant.id, user.email, user.displayName, user.role, false, true)

    private fun driver(tenant: TenantEntity) =
        users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "${UUID.randomUUID()}@wallet.test",
                displayName = "Conductrice",
                passwordHash = "x",
                role = UserRole.DRIVER,
            )
        )

    @Test
    fun `le passe Google Wallet est signe et porte le badge en Smart Tap`() {
        val tenant = tenants.save(TenantEntity(slug = "gw-${UUID.randomUUID()}", name = "Réseau Google"))
        val user = driver(tenant)
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "04AABBCCDD"), user.id))
        assertTrue(service.configured)
        assertTrue(service.nfcEnabled)
        val pass = service.pass(principal(tenant, user), "04AABBCCDD")
        assertTrue(pass.saveUrl.endsWith(pass.jwt))
        val parts = pass.jwt.split('.')
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(googlePair.public)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray())
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])))
        val claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]))
        assertEquals("google", claims.path("aud").asText())
        assertEquals("savetowallet", claims.path("typ").asText())
        val passClass = claims.path("payload").path("genericClasses").first()
        val passObject = claims.path("payload").path("genericObjects").first()
        assertTrue(passClass.path("enableSmartTap").asBoolean())
        assertEquals("3388000000000000001", passClass.path("redemptionIssuers").first().asText())
        assertEquals(passClass.path("id").asText(), passObject.path("classId").asText())
        assertEquals("04AABBCCDD", passObject.path("smartTapRedemptionValue").asText())
        assertEquals("04AABBCCDD", passObject.path("barcode").path("value").asText())
        assertTrue(passObject.path("textModulesData").toString().contains("https://codetics.fr"))
    }

    @Test
    fun `un badge d un autre conducteur est refuse`() {
        val tenant = tenants.save(TenantEntity(slug = "gw-${UUID.randomUUID()}", name = "Réseau Google"))
        val owner = driver(tenant)
        val other = driver(tenant)
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "04OWNER"), owner.id))
        assertThrows(Exception::class.java) { service.pass(principal(tenant, other), "04OWNER") }
    }

    @Test
    fun `un jeton FCM garde sa casse et un jeton APNs reste normalise`() {
        val tenant = tenants.save(TenantEntity(slug = "gw-${UUID.randomUUID()}", name = "Réseau Google"))
        val user = driver(tenant)
        val fcm = "fXyZ:APA91b_Mixed-Case_token_" + "a".repeat(120)
        push.register(principal(tenant, user), PushDeviceInput(token = fcm, platform = "ANDROID", locale = "fr"))
        assertEquals("ANDROID", devices.findById(fcm).orElseThrow().platform)
        assertTrue(push.deliverable(tenant.id, user.id))
        assertTrue(push.androidConfigured)
        assertThrows(Exception::class.java) {
            push.register(principal(tenant, user), PushDeviceInput(token = "not a token!", platform = "ANDROID"))
        }
        assertThrows(Exception::class.java) {
            push.register(principal(tenant, user), PushDeviceInput(token = fcm, platform = "WINDOWS"))
        }
        push.unregister(principal(tenant, user), fcm)
        assertFalse(devices.findById(fcm).isPresent)
        assertFalse(push.deliverable(tenant.id, user.id))
    }
}
