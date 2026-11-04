// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.customer

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.tokens.WalletPassService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.zip.ZipInputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

private fun rsa(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

private fun certificate(subject: String, issuer: String, key: KeyPair, signer: KeyPair): X509Certificate {
    val builder =
        JcaX509v3CertificateBuilder(
            X500Name("CN=$issuer"),
            BigInteger.valueOf(System.nanoTime()),
            Date(System.currentTimeMillis() - 60_000),
            Date(System.currentTimeMillis() + 3_600_000),
            X500Name("CN=$subject"),
            key.public,
        )
    return JcaX509CertificateConverter()
        .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signer.private)))
}

@ActiveProfiles("local")
@SpringBootTest(properties = ["app.bootstrap.email="])
class WalletPassTests
@Autowired
constructor(
    private val wallet: WalletPassService,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val tokens: ChargingTokenRepository,
) {
    companion object {
        private val root = rsa()
        private val leaf = rsa()
        private val wwdr = certificate("Test WWDR", "Test WWDR", root, root)
        private val signing = certificate("Pass Type ID", "Test WWDR", leaf, root)

        @JvmStatic
        @DynamicPropertySource
        fun credentials(registry: DynamicPropertyRegistry) {
            val store = KeyStore.getInstance("PKCS12")
            store.load(null, null)
            store.setKeyEntry("pass", leaf.private, "secret".toCharArray(), arrayOf(signing, wwdr))
            val output = ByteArrayOutputStream()
            store.store(output, "secret".toCharArray())
            val file = Files.createTempFile("wallet", ".p12")
            Files.write(file, output.toByteArray())
            registry.add("app.wallet.pass-type-id") { "pass.fr.codetics.badge" }
            registry.add("app.wallet.team-id") { "TEAM123456" }
            registry.add("app.wallet.certificate") { file.toString() }
            registry.add("app.wallet.certificate-password") { "secret" }
            registry.add("app.wallet.wwdr-certificate") { Base64.getEncoder().encodeToString(wwdr.encoded) }
            registry.add("app.wallet.nfc-public-key") { "MDkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDIgAD" }
        }
    }

    @Test
    fun `le passe est une archive signee contenant le badge et le NFC`() {
        val tenant = tenants.save(TenantEntity(slug = "wallet-${UUID.randomUUID()}", name = "Réseau Wallet"))
        val user =
            users.save(
                UserEntity(
                    tenantId = tenant.id,
                    email = "wallet-${UUID.randomUUID()}@example.test",
                    displayName = "Thomas Bernard",
                    role = UserRole.DRIVER,
                    emailVerified = true,
                )
            )
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "04A1B2C3D4E580"), user.id, visualId = "CARTE-12"))
        val principal = AppPrincipal(user.id, tenant.id, user.email, user.displayName, user.role)

        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(wallet.pass(principal, "04A1B2C3D4E580"))).use { zip ->
            generateSequence { zip.nextEntry }.forEach { files[it.name] = zip.readBytes() }
        }

        assertTrue(files.keys.containsAll(listOf("pass.json", "manifest.json", "signature", "icon.png", "icon@2x.png", "logo.png")))
        val pass = db.decode(String(files.getValue("pass.json")))
        assertEquals("pass.fr.codetics.badge", pass.path("passTypeIdentifier").asText())
        assertEquals("TEAM123456", pass.path("teamIdentifier").asText())
        assertEquals("04A1B2C3D4E580", pass.path("nfc").path("message").asText())
        assertEquals("04A1B2C3D4E580", pass.path("barcodes")[0].path("message").asText())
        assertEquals("Thomas Bernard", pass.path("storeCard").path("primaryFields")[0].path("value").asText())
        assertEquals("CARTE-12", pass.path("storeCard").path("secondaryFields")[0].path("value").asText())

        val manifest = db.decode(String(files.getValue("manifest.json")))
        files.filterKeys { it != "manifest.json" && it != "signature" }.forEach { (name, content) ->
            val digest = MessageDigest.getInstance("SHA-1").digest(content).joinToString("") { "%02x".format(it) }
            assertEquals(digest, manifest.path(name).asText(), name)
        }

        val signed = CMSSignedData(CMSProcessableByteArray(files.getValue("manifest.json")), files.getValue("signature"))
        assertEquals(2, signed.certificates.getMatches(null).size)
        val signer = signed.signerInfos.signers.single()
        assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(signing)))
        assertFalse(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(wwdr)))
    }
}
