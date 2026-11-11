// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

@file:UseSerializers(UuidSerializer::class, InstantSerializer::class, DecimalSerializer::class)

package fr.codetics.evapp.models

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
enum class UserRole {
    ADMIN,
    OPERATOR,
    VIEWER,
    DRIVER,
}

@Serializable
data class AppPrincipal(
    val id: UUID,
    val tenantId: UUID,
    val email: String,
    val name: String,
    val role: UserRole,
    val platformAdmin: Boolean,
    val eulaAccepted: Boolean,
)

@Serializable data class AppSessionResponse(val token: String, val expiresAt: Instant, val user: AppPrincipal)

@Serializable data class ChallengeRequest(val tenant: String, val email: String)

@Serializable data class CsrfResponse(val token: String, val headerName: String)

@Serializable data class EulaResponse(val version: String, val attribution: String, val url: String)

@Serializable data class LoginRequest(val tenant: String, val email: String, val password: String)

@Serializable data class SignupRequest(val email: String, val name: String, val password: String)

@Serializable
data class MobileConfigurationResponse(
    val server: String,
    val apiVersion: Int,
    val publicUrl: String,
    val signupEnabled: Boolean,
    val publicTenant: String? = null,
    val pushEnabled: Boolean,
    val walletEnabled: Boolean,
    val walletNfcEnabled: Boolean,
    val androidPushEnabled: Boolean = false,
    val googleWalletEnabled: Boolean = false,
    val googleWalletNfcEnabled: Boolean = false,
)

@Serializable
data class WorkspaceComponents(
    val organization: Boolean,
    val pricing: Boolean,
    val billing: Boolean,
    val roaming: Boolean,
    val smartCharging: Boolean,
    val statistics: Boolean,
    val assets: Boolean,
    val cars: Boolean,
    val publicCharging: Boolean,
)

@Serializable
data class WorkspaceResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val active: Boolean,
    val components: WorkspaceComponents,
    val logoUrl: String? = null,
)

@Serializable
data class ProfileInput(val name: String, val phone: String? = null, val locale: String? = null)

@Serializable
data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val enabled: Boolean,
    val emailVerified: Boolean,
    val phone: String? = null,
    val locale: String,
    val createdAt: Instant? = null,
    val lastLoginAt: Instant? = null,
    val billingCustomer: Boolean,
    val defaultCarId: UUID? = null,
    val siteCount: Int,
    val eulaAccepted: Boolean,
)

@Serializable
data class UserSiteResponse(val siteId: UUID, val name: String, val siteAdmin: Boolean, val siteOwner: Boolean)

@Serializable data class MutationResponse(val success: Boolean)

@Serializable data class PageResponse<Item>(val items: List<Item>, val total: Int)

@Serializable data class ProblemDetail(val status: Int? = null, val title: String? = null, val detail: String? = null, val code: String? = null)

@Serializable data class RecordInput<Payload>(val name: String, val data: Payload, val revision: Int? = null)

@Serializable
data class RecordResponse<Payload>(
    val id: UUID,
    val name: String,
    val data: Payload,
    val revision: Int,
    val updatedAt: Instant,
)

@Serializable data class RedirectResponse(val url: String)

@Serializable data class SavedRecord(val id: UUID, val revision: Int)
