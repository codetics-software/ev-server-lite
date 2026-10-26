package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.AppProperties
import java.io.StringWriter
import java.math.BigInteger
import java.net.InetAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChargerCertificatesTests {
    private val authorityKeys = keys()
    private val authority = certificate("CN=Codetics OCPP CA", authorityKeys, "CN=Codetics OCPP CA", authorityKeys, ca = true)
    private val stationKeys = keys()

    private fun keys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun certificate(
        subject: String,
        subjectKeys: KeyPair,
        issuer: String,
        issuerKeys: KeyPair,
        ca: Boolean = false,
        from: Instant = Instant.now().minus(Duration.ofDays(1)),
        to: Instant = Instant.now().plus(Duration.ofDays(30)),
    ): X509Certificate {
        val builder =
            JcaX509v3CertificateBuilder(
                X500Name(issuer),
                BigInteger.valueOf(System.nanoTime()),
                Date.from(from),
                Date.from(to),
                X500Name(subject),
                subjectKeys.public,
            )
        if (ca) builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        return JcaX509CertificateConverter()
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeys.private)))
    }

    private fun pem(value: Any): String =
        StringWriter().also { writer -> JcaPEMWriter(writer).use { it.writeObject(value) } }.toString()

    private fun service(signing: Boolean = false, proxies: String = "10.0.0.0/8") =
        ChargerCertificates(
            AppProperties(
                ocpp =
                    AppProperties.Ocpp(
                        trustedProxies = proxies,
                        clientCertHeader = "X-Client-Cert",
                        clientCa = pem(authority),
                        signingCaCertificate = if (signing) pem(authority) else "",
                        signingCaKey =
                            if (signing) {
                                "-----BEGIN PRIVATE KEY-----\n" +
                                    Base64.getMimeEncoder().encodeToString(authorityKeys.private.encoded) +
                                    "\n-----END PRIVATE KEY-----\n"
                            } else {
                                ""
                            },
                    )
            )
        )

    @Test
    fun `les certificats transmis par les mandataires sont decodes`() {
        val leaf = certificate("CN=CP-001,O=CPO", stationKeys, "CN=Codetics OCPP CA", authorityKeys)
        val certificates = service()
        val text = pem(leaf)
        val der = Base64.getEncoder().encodeToString(leaf.encoded)

        listOf(
                text,
                URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20"),
                URLEncoder.encode(text, StandardCharsets.UTF_8),
                ":$der:",
                der,
                "By=spiffe://proxy;Hash=abc;Cert=\"${URLEncoder.encode(text, StandardCharsets.UTF_8)}\";Subject=\"CN=CP-001\"",
                text.replace("\n", " "),
            )
            .forEach { header -> assertEquals(leaf, certificates.fromHeader(header).single(), header.take(40)) }
    }

    @Test
    fun `seule une chaine reconnue au nom de la borne est acceptee`() {
        val certificates = service()
        val leaf = certificate("CN=CP-001,O=CPO", stationKeys, "CN=Codetics OCPP CA", authorityKeys)
        assertEquals("CP-001", certificates.verify(listOf(leaf), "CP-001", null, null).commonName)

        assertEquals(
            "CN ≠ identifiant et empreinte non épinglée",
            assertThrows(CertificateRefused::class.java) {
                    certificates.verify(listOf(leaf), "CP-002", null, null)
                }
                .reason,
        )

        val otherKeys = keys()
        val foreign = certificate("CN=CP-001", stationKeys, "CN=Other CA", otherKeys)
        assertEquals(
            "chaîne non reconnue",
            assertThrows(CertificateRefused::class.java) { certificates.verify(listOf(foreign), "CP-001", null, null) }
                .reason,
        )

        val expired =
            certificate(
                "CN=CP-001",
                stationKeys,
                "CN=Codetics OCPP CA",
                authorityKeys,
                from = Instant.now().minus(Duration.ofDays(10)),
                to = Instant.now().minus(Duration.ofDays(1)),
            )
        assertEquals(
            "certificat expiré",
            assertThrows(CertificateRefused::class.java) { certificates.verify(listOf(expired), "CP-001", null, null) }
                .reason,
        )
    }

    @Test
    fun `une empreinte epinglee accepte un certificat d’usine et refuse les autres`() {
        val certificates = service()
        val factory = certificate("CN=SERIAL-123", stationKeys, "CN=Vendor CA", keys())
        val fingerprint = ChargerCertificates.fingerprint(factory).chunked(2).joinToString(":").uppercase()
        assertNotNull(certificates.verify(listOf(factory), "CP-001", fingerprint, null))

        val other = certificate("CN=CP-001", stationKeys, "CN=Codetics OCPP CA", authorityKeys)
        assertThrows(CertificateRefused::class.java) { certificates.verify(listOf(other), "CP-001", fingerprint, null) }
    }

    @Test
    fun `les demandes de certificat sont signees au nom de la borne uniquement`() {
        val certificates = service(signing = true)
        fun csr(name: String) =
            pem(
                JcaPKCS10CertificationRequestBuilder(X500Name(name), stationKeys.public)
                    .build(JcaContentSignerBuilder("SHA256withECDSA").build(stationKeys.private))
            )

        assertThrows(CertificateRequestRejected::class.java) { certificates.sign(csr("CN=CP-999,O=CPO"), "CP-001") }
        assertThrows(CertificateRequestRejected::class.java) { certificates.sign("pas une demande", "CP-001") }

        val signed = certificates.sign(csr("CN=CP-001,O=CPO"), "CP-001")
        val chain = certificates.fromHeader(signed.chain)
        assertEquals(2, chain.size)
        assertEquals(stationKeys.public, chain.first().publicKey)
        val fingerprint = ChargerCertificates.fingerprint(chain.first())

        assertNotNull(certificates.verify(chain, "CP-001", null, fingerprint))
        assertEquals(
            "certificat révoqué ou remplacé",
            assertThrows(CertificateRefused::class.java) { certificates.verify(chain, "CP-001", null, null) }.reason,
        )
    }

    @Test
    fun `l’adresse du client n’est lue que derriere un mandataire de confiance`() {
        val certificates = service()
        val proxy = InetAddress.ofLiteral("10.1.2.3")
        val outside = InetAddress.ofLiteral("203.0.113.9")

        assertTrue(certificates.trusted(proxy))
        assertFalse(certificates.trusted(outside))
        assertEquals("198.51.100.7", certificates.clientAddress(proxy, listOf("198.51.100.7, 10.0.0.5")))
        assertEquals("203.0.113.9", certificates.clientAddress(outside, listOf("198.51.100.7")))
        assertEquals("10.1.2.3", certificates.clientAddress(proxy, emptyList()))
        assertEquals("198.51.100.7", certificates.clientAddress(proxy, listOf("198.51.100.7:51234")))
        assertEquals("2001:db8:0:0:0:0:0:7", certificates.clientAddress(proxy, listOf("[2001:db8::7]:443")))
    }
}
