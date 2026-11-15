package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.ocpp.ChargerCertificates
import java.io.StringWriter
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder

object TestPki {
    class Authority(val keys: KeyPair, val certificate: X509Certificate)

    fun keys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun rsa(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    fun issue(
        subject: String,
        subjectKeys: KeyPair,
        issuer: Authority? = null,
        ca: Boolean = false,
        from: Instant = Instant.now().minus(Duration.ofDays(1)),
        until: Instant = Instant.now().plus(Duration.ofDays(365)),
        serial: BigInteger = BigInteger.valueOf(System.nanoTime()),
    ): X509Certificate {
        val issuerName = issuer?.let { X509CertificateHolder(it.certificate.encoded).subject } ?: X500Name(subject)
        val builder =
            JcaX509v3CertificateBuilder(issuerName, serial, Date.from(from), Date.from(until), X500Name(subject), subjectKeys.public)
        if (ca) {
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        }
        val signer = issuer?.keys?.private ?: subjectKeys.private
        return JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(signer)))
    }

    fun authority(subject: String, issuer: Authority? = null): Authority {
        val keys = keys()
        return Authority(keys, issue(subject, keys, issuer, ca = true))
    }

    fun pem(vararg certificates: X509Certificate) = certificates.joinToString("") { ChargerCertificates.encode(it) }

    fun pem(keys: KeyPair): String =
        StringWriter().also { writer -> JcaPEMWriter(writer).use { it.writeObject(JcaPKCS8Generator(keys.private, null)) } }.toString()

    fun csr(subject: String, keys: KeyPair, algorithm: String = "SHA256withECDSA"): String {
        val request = JcaPKCS10CertificationRequestBuilder(X500Name(subject), keys.public).build(JcaContentSignerBuilder(algorithm).build(keys.private))
        return StringWriter().also { writer -> JcaPEMWriter(writer).use { it.writeObject(request) } }.toString()
    }

    class Material(val directory: Path, val root: Authority, val cpo: Authority, val mo: Authority)

    fun write(directory: Path = Files.createTempDirectory("iso15118")): Material {
        val root = authority("CN=V2G Root Test,O=Test")
        val cpo = authority("CN=CPO Sub-CA Test,O=Test", root)
        val mo = authority("CN=MO Root Test,O=Test")
        Files.writeString(directory.resolve(Iso15118Certificates.V2G_ROOT), pem(root.certificate))
        Files.writeString(directory.resolve(Iso15118Certificates.CPO_CHAIN), pem(cpo.certificate))
        Files.writeString(directory.resolve(Iso15118Certificates.CPO_KEY), pem(cpo.keys))
        Files.writeString(directory.resolve(Iso15118Certificates.MO_ROOTS), pem(mo.certificate))
        return Material(directory, root, cpo, mo)
    }
}
