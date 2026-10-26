package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.ProxyTrust
import fr.codetics.ev_server.core.secureRandom
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.math.BigInteger
import java.net.InetAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.naming.ldap.LdapName
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.springframework.stereotype.Component

data class ClientCertificate(
    val certificate: X509Certificate,
    val chain: List<X509Certificate>,
) {
    val fingerprint: String = ChargerCertificates.fingerprint(certificate)
    val subject: String = certificate.subjectX500Principal.name.take(500)
    val commonName: String? = ChargerCertificates.commonName(certificate)
    val expiresAt: Instant = certificate.notAfter.toInstant()
}

data class SignedCertificate(val chain: String, val certificate: X509Certificate)

class CertificateRequestRejected(message: String) : RuntimeException(message)

class CertificateRefused(val reason: String, val certificate: ClientCertificate) : RuntimeException(reason)

@Component
class ChargerCertificates(private val properties: AppProperties) {
    private val factory = CertificateFactory.getInstance("X.509")
    private val proxies = ProxyTrust(properties.ocpp.trustedProxies)
    private val certificateProxies =
        properties.ocpp.clientCertProxies.takeIf(String::isNotBlank)?.let(::ProxyTrust) ?: proxies
    private val clientAuthorities: List<X509Certificate> = pem(properties.ocpp.clientCa)?.let(::certificates).orEmpty()
    private val signingAuthority: X509Certificate? =
        pem(properties.ocpp.signingCaCertificate)?.let(::certificates)?.firstOrNull()
    private val signingKey: PrivateKey? = pem(properties.ocpp.signingCaKey)?.let(::privateKey)

    init {
        require((signingAuthority == null) == (signingKey == null)) {
            "OCPP_SIGNING_CA_CERTIFICATE et OCPP_SIGNING_CA_KEY doivent être définis ensemble."
        }
    }

    val clientAuthorityConfigured: Boolean
        get() = clientAuthorities.isNotEmpty() || signingAuthority != null

    val signingConfigured: Boolean
        get() = signingAuthority != null && signingKey != null

    val clientCertificateHeader: String
        get() = properties.ocpp.clientCertHeader.trim()

    val proxiesConfigured: Boolean
        get() = proxies.configured

    fun trusted(address: InetAddress?): Boolean = proxies.trusted(address)

    fun certificateProxy(address: InetAddress?): Boolean = certificateProxies.trusted(address)

    fun clientAddress(peer: InetAddress?, forwardedFor: List<String>): String? = proxies.clientAddress(peer, forwardedFor)

    fun fromHeader(raw: String): List<X509Certificate> {
        var text = raw.trim()
        if (text.isEmpty() || text.length > 65536) return emptyList()

        Regex("""(?:^|[;,])\s*Cert="?([^";,]+)"?""").find(text)?.let { text = it.groupValues[1] }
        if (text.contains('%')) {
            text = URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8)
        }
        if (text.contains("-----BEGIN")) return certificates(text)

        return text.split(',').map { it.trim().trim(':') }.filter(String::isNotBlank).map { encoded ->
            certificate(Base64.getMimeDecoder().decode(encoded))
        }
    }

    fun verify(
        chain: List<X509Certificate>,
        stationId: String,
        pinned: String?,
        issued: String?,
    ): ClientCertificate {
        val presented = ClientCertificate(chain.first(), chain)
        try {
            presented.certificate.checkValidity()
        } catch (_: Exception) {
            throw CertificateRefused("certificat expiré", presented)
        }

        if (!pinned.isNullOrBlank()) {
            if (presented.fingerprint.equals(normalizeFingerprint(pinned), ignoreCase = true)) return presented
            throw CertificateRefused("empreinte différente de l’empreinte épinglée", presented)
        }
        val anchors = clientAuthorities + listOfNotNull(signingAuthority)
        if (anchors.isEmpty()) throw CertificateRefused("chaîne non reconnue", presented)
        try {
            val parameters = PKIXParameters(anchors.map { TrustAnchor(it, null) }.toSet()).apply {
                isRevocationEnabled = false
            }
            val path = factory.generateCertPath(chain.filterNot { it in anchors })
            CertPathValidator.getInstance("PKIX").validate(path, parameters)
        } catch (_: Exception) {
            throw CertificateRefused("chaîne non reconnue", presented)
        }
        if (presented.commonName != stationId) {
            throw CertificateRefused("CN ≠ identifiant et empreinte non épinglée", presented)
        }
        val signedHere =
            signingAuthority != null &&
                presented.certificate.issuerX500Principal == signingAuthority.subjectX500Principal
        if (signedHere && !presented.fingerprint.equals(issued, ignoreCase = true)) {
            throw CertificateRefused("certificat révoqué ou remplacé", presented)
        }
        return presented
    }

    fun sign(csrPem: String, stationId: String): SignedCertificate {
        val authority = signingAuthority ?: throw CertificateRequestRejected("Aucune autorité de signature configurée.")
        val key = signingKey ?: throw CertificateRequestRejected("Aucune autorité de signature configurée.")
        val request =
            try {
                PEMParser(StringReader(csrPem.trim())).use { it.readObject() } as PKCS10CertificationRequest
            } catch (_: Exception) {
                throw CertificateRequestRejected("Demande de certificat illisible.")
            }
        val verifier = JcaContentVerifierProviderBuilder().build(request.subjectPublicKeyInfo)
        if (!request.isSignatureValid(verifier)) {
            throw CertificateRequestRejected("Signature de la demande de certificat invalide.")
        }
        val commonName =
            request.subject.getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.toString()
        if (commonName != stationId) {
            throw CertificateRequestRejected("Le nom commun du certificat doit être l’identifiant de la borne.")
        }

        val now = Instant.now()
        val notAfter =
            minOf(
                now.plus(Duration.ofDays(properties.ocpp.signedCertificateDays.coerceIn(1, 3650))),
                authority.notAfter.toInstant(),
            )
        val publicKey = JcaPKCS10CertificationRequest(request).publicKey
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
                .addExtension(
                    Extension.keyUsage,
                    true,
                    KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.keyAgreement),
                )
                .addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth))
                .addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(publicKey))
                .addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(authority))
        val algorithm = if (key.algorithm == "RSA") "SHA256withRSA" else "SHA256withECDSA"
        val certificate =
            JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder(algorithm).build(key)))
        return SignedCertificate(encode(certificate) + encode(authority), certificate)
    }

    private fun pem(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (text.contains("-----BEGIN")) return text.replace("\\n", "\n")
        return Files.readString(Path.of(text))
    }

    private fun certificates(text: String): List<X509Certificate> =
        Regex("-----BEGIN[ +]CERTIFICATE-----(.*?)-----END[ +]CERTIFICATE-----", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { certificate(Base64.getMimeDecoder().decode(it.groupValues[1].filterNot(Char::isWhitespace))) }
            .toList()

    private fun certificate(der: ByteArray): X509Certificate =
        factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate

    private fun privateKey(text: String): PrivateKey {
        val converter = JcaPEMKeyConverter()
        return when (val parsed = PEMParser(StringReader(text)).use { it.readObject() }) {
            is PEMKeyPair -> converter.getPrivateKey(parsed.privateKeyInfo)
            is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(parsed)
            else -> throw IllegalArgumentException("Clé privée de l’autorité OCPP illisible.")
        }
    }

    companion object {
        fun fingerprint(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

        fun normalizeFingerprint(value: String): String = value.replace(":", "").replace(" ", "").lowercase()

        fun commonName(certificate: X509Certificate): String? =
            runCatching {
                    LdapName(certificate.subjectX500Principal.name).rdns.lastOrNull { it.type.equals("CN", true) }
                        ?.value
                        ?.toString()
                }
                .getOrNull()

        fun encode(certificate: X509Certificate): String =
            "-----BEGIN CERTIFICATE-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----\n"
    }
}
