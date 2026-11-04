// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.app

import fr.codetics.evapp.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import fr.codetics.evapp.BuildConfig
import fr.codetics.evapp.EvApp
import fr.codetics.evapp.core.ApiClient
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.core.AppConfiguration
import fr.codetics.evapp.core.ConfigurationIssue
import fr.codetics.evapp.core.ConfigurationOutcome
import fr.codetics.evapp.core.ConfigurationValidator
import fr.codetics.evapp.core.EvServerApi
import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.core.LocationProvider
import fr.codetics.evapp.core.SecureStore
import fr.codetics.evapp.demo.DemoSupport
import fr.codetics.evapp.models.*
import java.time.Instant
import java.util.Locale
import java.util.Properties
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

enum class AppTab {
    Map,
    Charge,
    Sessions,
    Badges,
    Account,
}

sealed interface Launch {
    data object Checking : Launch

    data class Misconfigured(val issues: List<ConfigurationIssue>) : Launch

    data class Unreachable(val message: String) : Launch

    data object Incompatible : Launch

    data object Ready : Launch
}

class ChargeStartException : Exception()

@Serializable
data class GuestOrder(
    @Serializable(with = fr.codetics.evapp.models.UuidSerializer::class) val id: UUID,
    val accessToken: String,
    val evseName: String,
    @Serializable(with = fr.codetics.evapp.models.InstantSerializer::class) val createdAt: Instant,
)

class AppModel(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var launch by mutableStateOf<Launch>(Launch.Checking)
        private set
    var principal by mutableStateOf<AppPrincipal?>(null)
        private set
    var configuration by mutableStateOf<AppConfiguration?>(null)
        private set
    var server by mutableStateOf<MobileConfigurationResponse?>(null)
        private set
    var workspace by mutableStateOf<WorkspaceResponse?>(null)
        private set
    var api by mutableStateOf<EvServerApi?>(null)
        private set
    var isDemo by mutableStateOf(false)
        private set
    var selectedTab by mutableStateOf(AppTab.Map)
    var alertMessage by mutableStateOf<String?>(null)

    val stacks: Map<AppTab, SnapshotStateList<Route>> =
        AppTab.entries.associateWith { mutableStateListOf(Route.Root(it)) }

    val stations = StationStore(this)
    val charging = ChargingStore(this)
    val push = PushCoordinator(this, context)
    val guestOrders = GuestOrderStore(this, context)
    val location = LocationProvider(context)

    private val secure = SecureStore(context, "session")
    private var bootstrapped = false
    private var pendingLink: PortalLink? = null
    private var pendingUri: Uri? = null

    val isSignedIn: Boolean
        get() = principal != null
    val brandName: String
        get() = configuration?.brandName ?: "EV App"
    val components: WorkspaceComponents?
        get() = workspace?.components
    val userLocation: Coordinate?
        get() = location.location?.let { Coordinate(it.latitude, it.longitude) }

    fun open(route: Route, tab: AppTab = selectedTab) {
        selectedTab = tab
        stacks.getValue(tab).add(route)
    }

    fun back(tab: AppTab = selectedTab): Boolean {
        val stack = stacks.getValue(tab)
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun resetStacks() {
        stacks.forEach { (tab, stack) ->
            stack.clear()
            stack.add(Route.Root(tab))
        }
    }

    fun bootstrap(demo: Boolean = false, demoGuest: Boolean = false, overrides: Map<String, String> = emptyMap()) {
        if (bootstrapped) return
        bootstrapped = true
        if (demo && DemoSupport.available) {
            startDemo(signIn = !demoGuest, googleMapsApiKey = overrides["GOOGLE_MAPS_API_KEY"])
            return
        }
        val values = readConfiguration().toMutableMap()
        if (BuildConfig.DEBUG) values += overrides
        when (val outcome = ConfigurationValidator.validate(values, allowInsecureLocalhost = BuildConfig.DEBUG)) {
            is ConfigurationOutcome.Invalid -> launch = Launch.Misconfigured(outcome.issues)
            is ConfigurationOutcome.Valid ->
                scope.launch {
                    connect(outcome.configuration, EvServerApi(ApiClient(outcome.configuration.apiUrl)))
                }
        }
    }

    private fun readConfiguration(): Map<String, String> =
        runCatching {
                val properties = Properties()
                context.assets.open("AppConfiguration.properties").use(properties::load)
                properties.stringPropertyNames().associateWith(properties::getProperty)
            }
            .getOrDefault(emptyMap())

    fun retryConnection() {
        bootstrapped = false
        launch = Launch.Checking
        bootstrap()
    }

    private suspend fun connect(configuration: AppConfiguration, api: EvServerApi) {
        this.configuration = configuration
        this.api = api
        pendingUri?.let {
            pendingUri = null
            handle(it)
        }
        configureFirebase(configuration)
        try {
            val server = api.configuration()
            if (server.server != "ev-supervision-lite" || server.apiVersion != 1) {
                launch = Launch.Incompatible
                return
            }
            this.server = server
            launch = Launch.Ready
            restoreSession()
            stations.reload()
            guestOrders.refresh()
            pendingLink?.let {
                pendingLink = null
                open(Route.Portal(it), AppTab.Charge)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            launch = Launch.Unreachable(message(error))
        }
    }

    private fun configureFirebase(configuration: AppConfiguration) {
        val firebase = configuration.firebase ?: return
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(firebase.projectId)
                    .setApplicationId(firebase.applicationId)
                    .setApiKey(firebase.apiKey)
                    .setGcmSenderId(firebase.senderId)
                    .build(),
            )
        }
    }

    private suspend fun restoreSession() {
        val api = api ?: return
        val token = secure.read("bearer") ?: return
        api.client.bearerToken = token
        try {
            enter(api.me())
        } catch (_: ApiError.Unauthorized) {
            forgetSession()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            alertMessage = message(error)
        }
    }

    suspend fun signIn(workspace: String, email: String, password: String) {
        val api = api ?: throw ApiError.InvalidResponse
        val session = api.signIn(LoginRequest(workspace.trim(), email.trim(), password))
        api.client.bearerToken = session.token
        if (!isDemo) secure.write("bearer", session.token)
        enter(session.user)
    }

    private suspend fun enter(principal: AppPrincipal) {
        this.principal = principal
        workspace = runCatching { api?.workspace() }.getOrNull()
        stations.reload()
        charging.refresh()
        push.start()
    }

    suspend fun refreshPrincipal() {
        principal = runCatching { api?.me() }.getOrNull() ?: return
    }

    suspend fun acceptEula() {
        api?.acceptEula()
        refreshPrincipal()
    }

    suspend fun signOut(everywhere: Boolean = false) {
        push.unregister()
        runCatching { if (everywhere) api?.signOutEverywhere() else api?.signOut() }
        forgetSession()
    }

    suspend fun deleteAccount() {
        push.unregister()
        api?.deleteAccount()
        forgetSession()
    }

    fun sessionExpired() {
        forgetSession()
        alertMessage = EvApp.string(R.string.your_session_has_expired_sign_in_again)
    }

    private fun forgetSession() {
        secure.delete("bearer")
        api?.client?.bearerToken = null
        principal = null
        workspace = null
        selectedTab = AppTab.Map
        resetStacks()
        charging.reset()
        scope.launch { stations.reload() }
    }

    fun handle(intent: Intent?) {
        intent ?: return
        intent.data?.let(::handle)
        intent.getStringExtra("transaction_id")?.toIntOrNull()?.let(::openNotification)
    }

    fun handle(uri: Uri) {
        val portal = configuration?.portalUrl ?: run {
            pendingUri = uri
            return
        }
        val parsed = QrCodeParser.parse(uri.toString(), portal)
        if (parsed is QrCodeParser.Outcome.Success) {
            if (launch == Launch.Ready) open(Route.Portal(parsed.link), AppTab.Charge) else pendingLink = parsed.link
        }
    }

    fun openNotification(transactionId: Int?) {
        if (!isSignedIn) return
        selectedTab = AppTab.Charge
        transactionId?.let {
            stacks.getValue(AppTab.Charge).apply {
                clear()
                add(Route.Root(AppTab.Charge))
                add(Route.LiveSession(it))
            }
        }
    }

    fun startDemo(signIn: Boolean, googleMapsApiKey: String? = null) {
        val configuration = DemoSupport.configuration()?.copy(googleMapsApiKey = googleMapsApiKey) ?: return
        isDemo = true
        bootstrapped = true
        launch = Launch.Checking
        principal = null
        scope.launch {
            connect(configuration, EvServerApi(ApiClient(configuration.apiUrl, DemoSupport.interceptor())))
            if (signIn) runCatching { signIn("demo", DemoSupport.EMAIL, DemoSupport.PASSWORD) }
        }
    }

    fun message(error: Throwable): String =
        when (error) {
            is ApiError.Unauthorized -> EvApp.string(R.string.your_session_has_expired_sign_in_again)
            is ApiError.Server -> error.detail ?: EvApp.string(R.string.the_server_refused_this_request)
            is ApiError.Offline -> EvApp.string(R.string.the_server_cannot_be_reached_check_your_connecti)
            is ApiError.InvalidResponse -> EvApp.string(R.string.the_server_returned_an_unexpected_response)
            is ChargeStartException ->
                EvApp.string(R.string.the_station_has_not_confirmed_the_start_check_th)
            else -> error.localizedMessage ?: EvApp.string(R.string.the_server_returned_an_unexpected_response)
        }
}

class StationStore(private val model: AppModel) {
    var stations by mutableStateOf<List<CustomerStationResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    suspend fun reload() {
        val api = model.api ?: return
        isLoading = true
        try {
            if (model.isSignedIn) {
                stations = api.stations()
            } else {
                model.configuration?.workspaceSlug?.let { stations = api.publicStations(it) }
            }
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApiError.Unauthorized) {
            model.sessionExpired()
        } catch (error: Exception) {
            errorMessage = model.message(error)
        } finally {
            isLoading = false
        }
    }

    fun station(id: String) = stations.firstOrNull { it.id == id }
}

class ChargingStore(private val model: AppModel) {
    var active by mutableStateOf<List<ChargingSessionResponse>>(emptyList())
        private set
    var tokens by mutableStateOf<List<ChargingTokenResponse>>(emptyList())
        private set

    val current: ChargingSessionResponse?
        get() = active.firstOrNull()

    val usableBadges: List<ChargingTokenResponse>
        get() =
            tokens
                .filter { it.enabled && it.vehicleId == null && !it.uid.uppercase().startsWith("VID:") }
                .filter { (it.expiresAt ?: Instant.MAX) > Instant.now() }
                .sortedByDescending { it.default }

    fun reset() {
        active = emptyList()
        tokens = emptyList()
    }

    suspend fun refresh() {
        val api = model.api ?: return
        if (!model.isSignedIn) return
        try {
            coroutineScope {
                val sessions = async { api.sessions(status = "active", limit = 10) }
                val owned = async { api.tokens() }
                active = sessions.await().items
                tokens = owned.await()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApiError.Unauthorized) {
            model.sessionExpired()
        } catch (_: Exception) {
            return
        }
    }

    suspend fun monitor() {
        while (true) {
            refresh()
            delay(if (active.isEmpty()) 30.seconds else 5.seconds)
        }
    }

    suspend fun start(station: String, connector: Int, badge: String): ChargingSessionResponse {
        val api = model.api ?: throw ApiError.InvalidResponse
        api.startSession(SessionStartInput(station, connector, badge))
        val session =
            withTimeoutOrNull(90.seconds) {
                while (true) {
                    delay(2.seconds)
                    val running = api.sessions(status = "active", stationId = station, limit = 10).items
                    running.firstOrNull { it.connectorId == connector }?.let { return@withTimeoutOrNull it }
                }
                @Suppress("UNREACHABLE_CODE")
                null
            } ?: throw ChargeStartException()
        refresh()
        return session
    }
}

class PushCoordinator(private val model: AppModel, private val context: Context) {
    var deviceToken by mutableStateOf<String?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var permissionRequest by mutableStateOf(false)

    val notificationsAllowed: Boolean
        get() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private val environment: String
        get() = if (BuildConfig.DEBUG) "sandbox" else "production"

    private val available: Boolean
        get() = model.server?.androidPushEnabled == true && !model.isDemo && FirebaseApp.getApps(context).isNotEmpty()

    fun start() {
        if (model.server?.androidPushEnabled != true) return
        if (!notificationsAllowed) permissionRequest = true
        if (!available) return
        model.scope.launch {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            token.onSuccess { deviceRegistered(it) }.onFailure { lastError = it.localizedMessage }
        }
    }

    suspend fun deviceRegistered(token: String) {
        deviceToken = token
        val api = model.api ?: return
        if (!model.isSignedIn) return
        try {
            api.registerDevice(PushDeviceInput(token, "ANDROID", environment, Locale.getDefault().language))
            lastError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            lastError = model.message(error)
        }
    }

    suspend fun unregister() {
        val token = deviceToken ?: return
        runCatching { model.api?.unregisterDevice(token) }
    }
}

class GuestOrderStore(private val model: AppModel, context: Context) {
    private val secure = SecureStore(context, "guest")

    var orders by mutableStateOf(load())
        private set

    private fun load(): List<GuestOrder> =
        runCatching {
                secure.read("orders")?.let {
                    JsonCoding.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GuestOrder.serializer()), it)
                }
            }
            .getOrNull()
            .orEmpty()

    private fun persist() {
        secure.write(
            "orders",
            JsonCoding.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GuestOrder.serializer()), orders),
        )
    }

    fun add(order: GuestOrder) {
        orders = listOf(order) + orders.filterNot { it.id == order.id }
        persist()
    }

    fun remove(id: UUID) {
        orders = orders.filterNot { it.id == id }
        persist()
    }

    suspend fun refresh() {
        val api = model.api ?: return
        for (order in orders) {
            try {
                val payment = api.guestSession(order.id, order.accessToken)
                if (payment.state.isFinished && order.createdAt < Instant.now().minusSeconds(86_400)) remove(order.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiError.Server) {
                if (error.status == 404) remove(order.id)
            } catch (_: Exception) {
                continue
            }
        }
    }
}
