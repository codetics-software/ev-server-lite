package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.ocpp.CertificateRequestRejected
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class Iso15118CertificatesTests {
    private fun store(directory: Path, enabled: Boolean = true) =
        Iso15118Certificates(AppProperties(ocpp = AppProperties.Ocpp(certificatesDir = directory.toString(), iso15118Enabled = enabled)))

    @Test
    fun `sans ISO 15118 le dossier des certificats n est pas exige`() {
        val store = store(Files.createTempDirectory("vide"), enabled = false)
        assertFalse(store.enabled)
        assertThrows(CertificateRequestRejected::class.java) { store.signV2g(TestPki.csr("CN=SECC", TestPki.keys())) }
    }

    @Test
    fun `avec ISO 15118 un dossier incomplet empeche le demarrage et liste les fichiers manquants`() {
        val error = assertThrows(Iso15118Configuration::class.java) { store(Files.createTempDirectory("vide")) }
        assertTrue(error.message!!.contains(Iso15118Certificates.V2G_ROOT))
        assertTrue(error.message!!.contains(Iso15118Certificates.CPO_CHAIN))
        assertTrue(error.message!!.contains(Iso15118Certificates.CPO_KEY))
    }

    @Test
    fun `une cle qui ne correspond pas a la sous-autorite est refusee au demarrage`() {
        val material = TestPki.write()
        Files.writeString(material.directory.resolve(Iso15118Certificates.CPO_KEY), TestPki.pem(TestPki.keys()))
        val error = assertThrows(Iso15118Configuration::class.java) { store(material.directory) }
        assertTrue(error.message!!.contains("ne correspond pas"))
    }

    @Test
    fun `une sous-autorite etrangere a la racine V2G est refusee au demarrage`() {
        val material = TestPki.write()
        val other = TestPki.authority("CN=Autre racine")
        val cpo = TestPki.authority("CN=CPO etranger", other)
        Files.writeString(material.directory.resolve(Iso15118Certificates.CPO_CHAIN), TestPki.pem(cpo.certificate))
        Files.writeString(material.directory.resolve(Iso15118Certificates.CPO_KEY), TestPki.pem(cpo.keys))
        val error = assertThrows(Iso15118Configuration::class.java) { store(material.directory) }
        assertTrue(error.message!!.contains("ne remonte pas"))
    }

    @Test
    fun `un certificat V2G est signe par la sous-autorite CPO et remonte a la racine`() {
        val material = TestPki.write()
        val store = store(material.directory)
        assertTrue(store.enabled)

        val signed = store.signV2g(TestPki.csr("CN=FRCODE1234,O=Test", TestPki.keys()))
        val chain = store.parse(signed.chain)
        assertEquals(2, chain.size)
        assertEquals(material.cpo.certificate, chain[1])
        assertTrue(chain[0].subjectX500Principal.name.contains("FRCODE1234"))
        val factory = CertificateFactory.getInstance("X.509")
        CertPathValidator.getInstance("PKIX").validate(
            factory.generateCertPath(chain),
            PKIXParameters(setOf(TrustAnchor(material.root.certificate, null))).apply { isRevocationEnabled = false },
        )

        assertThrows(CertificateRequestRejected::class.java) { store.signV2g(TestPki.csr("CN=RSA", TestPki.rsa(), "SHA256withRSA")) }
        assertThrows(CertificateRequestRejected::class.java) { store.signV2g("pas une demande") }
    }

    @Test
    fun `les certificats de contrat sont verifies par rapport aux racines MO et V2G`() {
        val material = TestPki.write()
        val store = store(material.directory)
        val contract = TestPki.issue("CN=FRCODC123456789", TestPki.keys(), material.mo)
        assertEquals(ContractVerdict.VALID, store.verifyContract(TestPki.pem(contract)))

        val expired =
            TestPki.issue(
                "CN=FRCODC000000000",
                TestPki.keys(),
                material.mo,
                from = Instant.now().minus(Duration.ofDays(30)),
                until = Instant.now().minus(Duration.ofDays(1)),
            )
        assertEquals(ContractVerdict.EXPIRED, store.verifyContract(TestPki.pem(expired)))

        val stranger = TestPki.issue("CN=Inconnu", TestPki.keys(), TestPki.authority("CN=Racine inconnue"))
        assertEquals(ContractVerdict.CHAIN_ERROR, store.verifyContract(TestPki.pem(stranger)))
        assertEquals(ContractVerdict.CHAIN_ERROR, store.verifyContract("illisible"))
    }

    @Test
    fun `le script de creation des certificats produit un dossier accepte au demarrage`() {
        val script = Path.of("..", "scripts", "create-iso15118-certificates.sh").toAbsolutePath().normalize()
        assumeTrue(Files.isExecutable(script))
        assumeTrue(runCatching { ProcessBuilder("openssl", "version").start().waitFor() == 0 }.getOrDefault(false))
        val directory = Files.createTempDirectory("script")
        val process = ProcessBuilder(script.toString(), directory.toString()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)

        val store = store(directory)
        assertEquals(2, store.cpoChain.size)
        assertEquals(1, store.moRoots.size)
        val signed = store.signV2g(TestPki.csr("CN=SCRIPT01", TestPki.keys()))
        assertEquals(3, store.parse(signed.chain).size)
    }
}
