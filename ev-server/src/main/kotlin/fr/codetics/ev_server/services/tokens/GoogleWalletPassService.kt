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
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.ocpp.VehicleIds
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.services.google.GoogleServiceAccount
import java.time.Clock
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class GoogleWalletPassResponse(val jwt: String, val saveUrl: String)

@Service
class GoogleWalletPassService(
    private val properties: AppProperties,
    private val tokens: ChargingTokenRepository,
    private val tenants: TenantRepository,
    private val db: Database,
    private val json: ObjectMapper,
    private val clock: Clock,
) {
    private val settings = properties.googleWallet
    private val account by lazy { GoogleServiceAccount.read(settings.serviceAccount, json) }

    val configured: Boolean
        get() = settings.issuerId.isNotBlank() && settings.serviceAccount.isNotBlank()

    val nfcEnabled: Boolean
        get() = configured && settings.smartTapRedemptionIssuer.isNotBlank()

    fun pass(user: AppPrincipal, uid: String): GoogleWalletPassResponse {
        val token =
            tokens.findById(ChargingTokenKey(user.tenantId, uid)).orElse(null)?.takeIf { it.userId == user.id }
                ?: throw NotFound(msg("tokens.not_found"))
        ensure(!VehicleIds.isVid(uid)) { msg("wallet.google.vehicle_id") }
        ensure(token.enabled) { msg("tokens.disabled") }
        if (!configured) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg("wallet.google.not_configured"))
        }
        val workspace = tenants.findById(user.tenantId).orElseThrow()
        val jwt = account.sign(claims(workspace, user, uid, token.visualId, token.expiresAt))
        db.audit(user.tenantId, user.id.toString(), "GOOGLE_WALLET_PASS_ISSUE", uid)
        return GoogleWalletPassResponse(jwt = jwt, saveUrl = "$SAVE_URL/$jwt")
    }

    fun claims(workspace: TenantEntity, user: AppPrincipal, uid: String, visualId: String?, expiresAt: Instant?) =
        mapOf(
            "iss" to account.clientEmail,
            "aud" to "google",
            "typ" to "savetowallet",
            "iat" to Instant.now(clock).epochSecond,
            "origins" to listOf(properties.publicUrl.trimEnd('/')),
            "payload" to
                mapOf(
                    "genericClasses" to listOf(passClass(workspace)),
                    "genericObjects" to listOf(passObject(workspace, user, uid, visualId, expiresAt)),
                ),
        )

    private fun classId(workspace: TenantEntity) =
        "${settings.issuerId}.${settings.classSuffix}_${workspace.slug.replace('-', '_')}"

    private fun passClass(workspace: TenantEntity) =
        buildMap<String, Any?> {
            put("id", classId(workspace))
            if (nfcEnabled) {
                put("enableSmartTap", true)
                put("redemptionIssuers", listOf(settings.smartTapRedemptionIssuer))
            }
        }

    private fun passObject(
        workspace: TenantEntity,
        user: AppPrincipal,
        uid: String,
        visualId: String?,
        expiresAt: Instant?,
    ) =
        buildMap<String, Any?> {
            put("id", "${settings.issuerId}.${sha256("${user.tenantId}:$uid").take(32)}")
            put("classId", classId(workspace))
            put("state", "ACTIVE")
            put("hexBackgroundColor", "#006038")
            put("cardTitle", text(settings.organizationName.ifBlank { workspace.name }))
            put("subheader", text("Conducteur"))
            put("header", text(user.name))
            put("barcode", mapOf("type" to "QR_CODE", "value" to uid, "alternateText" to (visualId ?: uid)))
            put(
                "textModulesData",
                listOf(
                    mapOf("id" to "badge", "header" to msg("wallet.badge"), "body" to (visualId ?: uid)),
                    mapOf("id" to "workspace", "header" to msg("wallet.network"), "body" to workspace.name),
                    mapOf("id" to "credit", "header" to msg("wallet.made_with"), "body" to ATTRIBUTION),
                ),
            )
            if (workspace.hasLogo) {
                val logo = "${properties.publicUrl.trimEnd('/')}/api/v1/public/workspaces/${workspace.slug}/logo"
                put("logo", mapOf("sourceUri" to mapOf("uri" to logo)))
            }
            expiresAt?.let { put("validTimeInterval", mapOf("end" to mapOf("date" to it.toString()))) }
            if (nfcEnabled) put("smartTapRedemptionValue", uid)
        }

    private fun text(value: String) =
        mapOf("defaultValue" to mapOf("language" to "fr", "value" to value))

    private companion object {
        const val SAVE_URL = "https://pay.google.com/gp/v/save"
        const val ATTRIBUTION = "EV Supervision Lite — Kevin Vavelin, Codetics — https://codetics.fr"
    }
}
