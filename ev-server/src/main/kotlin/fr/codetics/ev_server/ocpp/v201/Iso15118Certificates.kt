package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.secureRandom
import fr.codetics.ev_server.ocpp.CertificateRequestRejected
import fr.codetics.ev_server.ocpp.ChargerCertificates
import fr.codetics.ev_server.ocpp.SignedCertificate
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.springframework.stereotype.Component
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CertificateSummary(val subject: String, val expiresAt: Instant, val fingerprint: String) {
    companion object {
        fun of(certificate: X509Certificate) =
            CertificateSummary(
                certificate.subjectX500Principal.name.take(500),
                certificate.notAfter.toInstant(),
                ChargerCertificates.fingerprint(certificate),
            )
    }
}

enum class ContractVerdict { VALID, EXPIRED, CHAIN_ERROR }

class Iso15118Configuration(message: String) : IllegalStateException(message)

@Component
class Iso15118Certificates(private val properties: AppProperties) {
    private val factory = CertificateFactory.getInstance("X.509")
    private val material: Material? = if (properties.ocpp.iso15118Enabled) load(Path.of(properties.ocpp.certificatesDir)) else null

    private class Material(
        val v2gRoots: List<X509Certificate>,
        val moRoots: List<X509Certificate>,
        val cpoChain: List<X509Certificate>,
        val cpoKey: PrivateKey,
    )

    val enabled: Boolean
        get() = material != null

    val directory: Path
        get() = Path.of(properties.ocpp.certificatesDir).toAbsolutePath()

    val v2gRoots: List<X509Certificate>
        get() = material?.v2gRoots.orEmpty()

    val moRoots: List<X509Certificate>
        get() = material?.moRoots.orEmpty()

    val cpoChain: List<X509Certificate>
        get() = material?.cpoChain.orEmpty()

    private fun load(directory: Path): Material {
        val problems = mutableListOf<String>()
        fun read(name: String, required: Boolean): String? {
            val file = directory.resolve(name)
            if (!Files.isRegularFile(file)) {
                if (required) problems += "$name absent"
                return null
            }
            return runCatching { Files.readString(file) }.getOrElse {
                problems += "$name illisible"
                null
            }
        }
        fun certificates(name: String, required: Boolean): List<X509Certificate> {
            val text = read(name, required) ?: return emptyList()
            val parsed = runCatching { parse(text) }.getOrElse { emptyList() }
            if (parsed.isEmpty()) problems += "$name ne contient aucun certificat PEM lisible"
            parsed.forEach { certificate ->
                if (runCatching { certificate.checkValidity() }.isFailure) {
                    problems += "$name : ${certificate.subjectX500Principal.name} est expiré ou pas encore valide"
                }
            }
            return parsed
        }

        val roots = certificates(V2G_ROOT, true)
        roots.filter { it.basicConstraints < 0 }.forEach { problems += "$V2G_ROOT : ${it.subjectX500Principal.name} n’est pas une autorité de certification" }
        val chain = certificates(CPO_CHAIN, true)
        chain.firstOrNull()?.takeIf { it.basicConstraints < 0 }?.let {
            problems += "$CPO_CHAIN : le premier certificat doit être la sous-autorité CPO qui signe les certificats des bornes"
        }
        val moRoots = certificates(MO_ROOTS, false)
        val key =
            read(CPO_KEY, true)?.let { text ->
                runCatching { privateKey(text) }.getOrElse {
                    problems += "$CPO_KEY : clé privée illisible"
                    null
                }
            }

        if (roots.isNotEmpty() && chain.isNotEmpty() && !chains(chain, roots)) {
            problems += "$CPO_CHAIN ne remonte pas jusqu’à une racine de $V2G_ROOT"
        }
        if (key != null && chain.isNotEmpty()) {
            if (key.algorithm != "EC" && key.algorithm != "ECDSA") problems += "$CPO_KEY doit être une clé ECDSA (ISO 15118)"
            else if (!matches(key, chain.first())) problems += "$CPO_KEY ne correspond pas au premier certificat de $CPO_CHAIN"
        }

        if (problems.isNotEmpty() || key == null) {
            throw Iso15118Configuration(
                "ISO 15118 est activé (OCPP_ISO15118_ENABLED=true) mais le dossier des certificats " +
                    "${directory.toAbsolutePath()} est incomplet ou invalide :\n" +
                    problems.joinToString("\n") { " - $it" } +
                    "\nDéposez les fichiers $V2G_ROOT, $CPO_CHAIN et $CPO_KEY (et facultativement $MO_ROOTS) avant de démarrer le serveur."
            )
        }
        return Material(roots, moRoots, chain, key)
    }

    fun signV2g(csrPem: String): SignedCertificate {
        val loaded = material ?: throw CertificateRequestRejected("ISO 15118 n’est pas activé sur le serveur.")
        val request =
            try {
                PEMParser(StringReader(csrPem.trim())).use { it.readObject() } as PKCS10CertificationRequest
            } catch (_: Exception) {
                throw CertificateRequestRejected("Demande de certificat illisible.")
            }
        if (!request.isSignatureValid(JcaContentVerifierProviderBuilder().build(request.subjectPublicKeyInfo))) {
            throw CertificateRequestRejected("Signature de la demande de certificat invalide.")
        }
        val publicKey = JcaPKCS10CertificationRequest(request).publicKey
        if (publicKey !is ECPublicKey || publicKey.params.curve.field.fieldSize != 256) {
            throw CertificateRequestRejected("Un certificat V2G exige une clé ECDSA secp256r1.")
        }
        val authority = loaded.cpoChain.first()
        val now = Instant.now()
        val notAfter =
            minOf(
                now.plus(Duration.ofDays(properties.ocpp.v2gCertificateDays.coerceIn(1, 3650))),
                authority.notAfter.toInstant(),
            )
        val extensions = JcaX509ExtensionUtils()
        val builder =
            JcaX509v3CertificateBuilder(
                    X509CertificateHolder(authority.encoded).subject,
                    BigInteger(127, secureRandom).add(BigInteger.ONE),
                    Date.from(now.minus(Duration.ofMinutes(5))),
                    Date.from(notAfter),
                    request.subject,
                    publicKey,
                )
                .addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyAgreement))
                .addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(publicKey))
                .addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(authority))
        val certificate =
            JcaX509CertificateConverter()
                .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(loaded.cpoKey)))
        val chain = (listOf(certificate) + loaded.cpoChain).joinToString("") { ChargerCertificates.encode(it) }
        return SignedCertificate(chain, certificate)
    }

    fun verifyContract(pem: String): ContractVerdict {
        val chain = runCatching { parse(pem) }.getOrNull().orEmpty()
        if (chain.isEmpty()) return ContractVerdict.CHAIN_ERROR
        if (chain.any { runCatching { it.checkValidity() }.isFailure }) return ContractVerdict.EXPIRED
        return if (chains(chain, moRoots + v2gRoots)) ContractVerdict.VALID else ContractVerdict.CHAIN_ERROR
    }

    fun parse(text: String): List<X509Certificate> =
        Regex("-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { match ->
                factory.generateCertificate(
                    ByteArrayInputStream(Base64.getMimeDecoder().decode(match.groupValues[1].filterNot(Char::isWhitespace)))
                ) as X509Certificate
            }
            .toList()

    private fun chains(chain: List<X509Certificate>, anchors: List<X509Certificate>): Boolean {
        if (anchors.isEmpty()) return false
        return runCatching {
                val parameters =
                    PKIXParameters(anchors.map { TrustAnchor(it, null) }.toSet()).apply { isRevocationEnabled = false }
                CertPathValidator.getInstance("PKIX").validate(factory.generateCertPath(chain.filterNot { it in anchors }), parameters)
            }
            .isSuccess
    }

    private fun matches(key: PrivateKey, certificate: X509Certificate): Boolean =
        runCatching {
                val data = "iso15118".toByteArray()
                val signed = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(data) }.sign()
                Signature.getInstance("SHA256withECDSA").apply { initVerify(certificate.publicKey); update(data) }.verify(signed)
            }
            .getOrDefault(false)

    private fun privateKey(text: String): PrivateKey {
        val converter = JcaPEMKeyConverter().setProvider(BouncyCastleProvider())
        PEMParser(StringReader(text)).use { parser ->
            while (true) {
                val info =
                    when (val parsed = parser.readObject() ?: break) {
                        is PEMKeyPair -> parsed.privateKeyInfo
                        is PrivateKeyInfo -> parsed
                        else -> continue
                    }
                val decoded = converter.getPrivateKey(info)
                return KeyFactory.getInstance(decoded.algorithm.replace("ECDSA", "EC")).generatePrivate(PKCS8EncodedKeySpec(decoded.encoded))
            }
        }
        throw IllegalArgumentException("Clé privée illisible.")
    }

    companion object {
        const val V2G_ROOT = "v2g-root.pem"
        const val CPO_CHAIN = "cpo-sub-ca.pem"
        const val CPO_KEY = "cpo-sub-ca.key"
        const val MO_ROOTS = "mo-root.pem"
    }
}
