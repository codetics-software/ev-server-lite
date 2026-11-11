// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.tokens

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.ocpp.VehicleIds
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.TenantRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class WalletPassService(
    properties: AppProperties,
    private val tokens: ChargingTokenRepository,
    private val tenants: TenantRepository,
    private val db: Database,
) {
    private val settings = properties.wallet

    private data class Credentials(val key: PrivateKey, val certificate: X509Certificate, val wwdr: X509Certificate)

    private val credentials: Credentials? by lazy { if (configured) load() else null }

    val configured: Boolean
        get() =
            settings.passTypeId.isNotBlank() &&
                settings.teamId.isNotBlank() &&
                settings.certificate.isNotBlank() &&
                settings.wwdrCertificate.isNotBlank()

    val nfcEnabled: Boolean
        get() = configured && settings.nfcPublicKey.isNotBlank()

    fun pass(user: AppPrincipal, uid: String): ByteArray {
        val token =
            tokens.findById(ChargingTokenKey(user.tenantId, uid)).orElse(null)?.takeIf { it.userId == user.id }
                ?: throw NotFound(msg("tokens.not_found"))
        ensure(!VehicleIds.isVid(uid)) { msg("wallet.apple.vehicle_id") }
        ensure(token.enabled) { msg("tokens.disabled") }
        val signing =
            credentials
                ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg("wallet.apple.not_configured"))
        val workspace = tenants.findById(user.tenantId).orElseThrow()
        val files = linkedMapOf<String, ByteArray>()
        files["pass.json"] =
            db.encode(document(workspace.name, user, uid, token.visualId, token.expiresAt?.toString())).toByteArray()
        ASSETS.forEach { name -> files[name] = ClassPathResource("wallet/$name").inputStream.use { it.readBytes() } }
        val manifest = db.encode(files.mapValues { (_, content) -> sha1(content) }).toByteArray()
        files["manifest.json"] = manifest
        files["signature"] = sign(manifest, signing)
        db.audit(user.tenantId, user.id.toString(), "WALLET_PASS_ISSUE", uid)
        return zip(files)
    }

    private fun document(workspace: String, user: AppPrincipal, uid: String, visualId: String?, expiresAt: String?) =
        buildMap<String, Any?> {
            put("formatVersion", 1)
            put("passTypeIdentifier", settings.passTypeId)
            put("teamIdentifier", settings.teamId)
            put("serialNumber", sha256("${user.tenantId}:$uid").take(32))
            put("organizationName", settings.organizationName.ifBlank { workspace })
            put("description", msg("wallet.description", workspace))
            put("logoText", workspace.take(30))
            put("foregroundColor", "rgb(255, 255, 255)")
            put("labelColor", "rgb(200, 240, 220)")
            put("backgroundColor", "rgb(0, 96, 56)")
            put("sharingProhibited", true)
            expiresAt?.let { put("expirationDate", it) }
            put(
                "storeCard",
                mapOf(
                    "primaryFields" to listOf(mapOf("key" to "holder", "label" to msg("wallet.driver"), "value" to user.name)),
                    "secondaryFields" to listOf(mapOf("key" to "badge", "label" to msg("wallet.badge"), "value" to (visualId ?: uid))),
                    "backFields" to
                        listOf(
                            mapOf("key" to "workspace", "label" to msg("wallet.network"), "value" to workspace),
                            mapOf("key" to "credit", "label" to msg("wallet.made_with"), "value" to "EV Supervision Lite — Kevin Vavelin, Codetics — https://codetics.fr"),
                        ),
                ),
            )
            put(
                "barcodes",
                listOf(mapOf("format" to "PKBarcodeFormatQR", "message" to uid, "messageEncoding" to "iso-8859-1")),
            )
            if (settings.nfcPublicKey.isNotBlank()) {
                put("nfc", mapOf("message" to uid, "encryptionPublicKey" to settings.nfcPublicKey))
            }
        }

    private fun sign(manifest: ByteArray, signing: Credentials): ByteArray {
        val generator = CMSSignedDataGenerator()
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(signing.key)
        val digests = JcaDigestCalculatorProviderBuilder().build()
        generator.addSignerInfoGenerator(JcaSignerInfoGeneratorBuilder(digests).build(signer, signing.certificate))
        generator.addCertificates(JcaCertStore(listOf(signing.certificate, signing.wwdr)))
        return generator.generate(CMSProcessableByteArray(manifest), false).encoded
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { archive ->
            files.forEach { (name, content) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(content)
                archive.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha1(content: ByteArray) =
        MessageDigest.getInstance("SHA-1").digest(content).joinToString("") { "%02x".format(it) }

    private fun load(): Credentials {
        val store = KeyStore.getInstance("PKCS12")
        val password = settings.certificatePassword.toCharArray()
        ByteArrayInputStream(material(settings.certificate)).use { store.load(it, password) }
        val alias = store.aliases().toList().first(store::isKeyEntry)
        val wwdr =
            ByteArrayInputStream(certificate(settings.wwdrCertificate)).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
        return Credentials(store.getKey(alias, password) as PrivateKey, store.getCertificate(alias) as X509Certificate, wwdr)
    }

    private fun material(value: String): ByteArray {
        val path = runCatching { Path.of(value) }.getOrNull()
        return if (path != null && Files.isRegularFile(path)) Files.readAllBytes(path)
        else Base64.getMimeDecoder().decode(value)
    }

    private fun certificate(value: String): ByteArray =
        if (value.contains("BEGIN CERTIFICATE")) value.replace("\\n", "\n").toByteArray() else material(value)

    private companion object {
        val ASSETS = listOf("icon.png", "icon@2x.png", "icon@3x.png", "logo.png", "logo@2x.png")
    }
}
