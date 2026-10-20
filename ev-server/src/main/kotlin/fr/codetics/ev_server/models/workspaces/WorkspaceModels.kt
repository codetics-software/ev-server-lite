package fr.codetics.ev_server.models.workspaces

import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class WorkspaceComponents(
    val organization: Boolean = true,
    val pricing: Boolean = true,
    val billing: Boolean = false,
    val roaming: Boolean = true,
    val smartCharging: Boolean = false,
    val statistics: Boolean = true,
    val assets: Boolean = false,
    val cars: Boolean = true,
    val publicCharging: Boolean = true,
)

data class WorkspaceComponentsInput(
    val organization: Boolean = true,
    val pricing: Boolean = true,
    val billing: Boolean = false,
    val roaming: Boolean = true,
    val smartCharging: Boolean = false,
    val statistics: Boolean = true,
    val assets: Boolean = false,
    val cars: Boolean = true,
    val publicCharging: Boolean = true,
) {
    fun toComponents() =
        WorkspaceComponents(
            organization,
            pricing,
            billing,
            roaming,
            smartCharging,
            statistics,
            assets,
            cars,
            publicCharging,
        )
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class WorkspaceResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val active: Boolean,
    val components: WorkspaceComponents = WorkspaceComponents(),
    val logoUrl: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PlatformWorkspaceResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val active: Boolean,
    val createdAt: Instant,
    val components: WorkspaceComponents,
    val users: Long,
    val stations: Long,
)

data class PlatformWorkspaceInput(
    val name: String,
    val active: Boolean,
    val components: WorkspaceComponentsInput,
)

data class WorkspaceUpdate(val name: String)

data class WorkspaceCreation(
    val name: String,
    val slug: String,
    val administratorName: String,
    val email: String,
    val password: String,
)

data class WorkspacePaymentInput(val enabled: Boolean, val country: String = "FR")

data class WorkspacePaymentResponse(
    val configured: Boolean,
    val enabled: Boolean,
    val accountId: String?,
    val ready: Boolean,
    val country: String,
    val currency: String,
    val userBilling: UserBillingSettings = UserBillingSettings(),
)

data class UserBillingSettings(
    val enabled: Boolean = false,
    val mode: String = "PER_SESSION",
    val taxRateId: String? = null,
)

data class UserBillingInput(val enabled: Boolean, val mode: String = "PER_SESSION", val taxRateId: String? = null)

data class RedirectResponse(val url: String)
