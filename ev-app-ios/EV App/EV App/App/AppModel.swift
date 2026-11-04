//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Observation

@Observable
final class AppModel {
    static let shared = AppModel()

    enum Launch: Equatable {
        case checking
        case misconfigured([ConfigurationIssue])
        case unreachable(String)
        case incompatible
        case ready
    }

    enum Account: Equatable {
        case signedOut
        case signedIn(AppPrincipal)
    }

    private(set) var launch: Launch = .checking
    private(set) var account: Account = .signedOut
    private(set) var configuration: AppConfiguration?
    private(set) var server: MobileConfigurationResponse?
    private(set) var workspace: WorkspaceResponse?
    private(set) var api: EVServerAPI?
    private(set) var isDemo = false

    var selectedTab: AppTab = .map
    var pendingPortalLink: PortalLink?
    var openedSessionId: Int?
    var alertMessage: String?

    let stations = StationStore()
    let charging = ChargingStore()
    let push = PushCoordinator()
    let guestOrders = GuestOrderStore()
    let location = LocationProvider()

    private let keychain = KeychainStore(service: "fr.codetics.evapp.session")
    private var hasBootstrapped = false

    var principal: AppPrincipal? {
        if case .signedIn(let principal) = account { return principal }
        return nil
    }

    var isSignedIn: Bool { principal != nil }

    var brandName: String { configuration?.brandName ?? "EV App" }

    var components: WorkspaceComponents? { workspace?.components }

    func bootstrap() async {
        guard !hasBootstrapped else { return }
        hasBootstrapped = true
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-demoAccount") {
            await startDemo(signIn: !ProcessInfo.processInfo.arguments.contains("-demoGuest"))
            return
        }
        #endif
        switch ConfigurationValidator.loadBundled() {
        case .invalid(let issues):
            launch = .misconfigured(issues)
        case .valid(let configuration):
            await connect(configuration, api: EVServerAPI(client: APIClient(baseURL: configuration.apiURL)))
        }
    }

    func retryConnection() async {
        hasBootstrapped = false
        launch = .checking
        await bootstrap()
    }

    private func connect(_ configuration: AppConfiguration, api: EVServerAPI) async {
        self.configuration = configuration
        self.api = api
        do {
            let server = try await api.configuration()
            guard server.server == "ev-supervision-lite", server.apiVersion == 1 else {
                launch = .incompatible
                return
            }
            self.server = server
            launch = .ready
            await restoreSession()
            await stations.reload()
            await guestOrders.refresh()
        } catch {
            launch = .unreachable(error.localizedDescription)
        }
    }

    private func restoreSession() async {
        guard let api, let data = keychain.read("bearer"), let token = String(data: data, encoding: .utf8) else { return }
        api.client.bearerToken = token
        do {
            let principal = try await api.me()
            await enter(principal)
        } catch APIError.unauthorized {
            forgetSession()
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    func signIn(workspace: String, email: String, password: String) async throws {
        guard let api else { throw APIError.invalidResponse }
        let session = try await api.signIn(
            LoginRequest(
                tenant: workspace.trimmingCharacters(in: .whitespacesAndNewlines),
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
        )
        api.client.bearerToken = session.token
        if !isDemo { keychain.write(Data(session.token.utf8), account: "bearer") }
        await enter(session.user)
    }

    private func enter(_ principal: AppPrincipal) async {
        account = .signedIn(principal)
        do {
            workspace = try await api?.workspace()
        } catch {
            workspace = nil
        }
        await stations.reload()
        await charging.refresh()
        await push.requestAuthorization()
    }

    func refreshPrincipal() async {
        guard let principal = try? await api?.me() else { return }
        account = .signedIn(principal)
    }

    func acceptEula() async throws {
        try await api?.acceptEula()
        await refreshPrincipal()
    }

    func signOut(everywhere: Bool = false) async {
        await push.unregister()
        if everywhere {
            try? await api?.signOutEverywhere()
        } else {
            try? await api?.signOut()
        }
        forgetSession()
    }

    func deleteAccount() async throws {
        await push.unregister()
        try await api?.deleteAccount()
        forgetSession()
    }

    func sessionExpired() {
        forgetSession()
        alertMessage = APIError.unauthorized.errorDescription
    }

    private func forgetSession() {
        keychain.delete("bearer")
        api?.client.bearerToken = nil
        account = .signedOut
        workspace = nil
        selectedTab = .map
        charging.reset()
        Task { await stations.reload() }
    }

    func handle(url: URL) {
        guard let portal = configuration?.portalURL else { return }
        if case .success(let link) = QRCodeParser.parse(url.absoluteString, portalURL: portal) {
            pendingPortalLink = link
            selectedTab = .charge
        }
    }

    func openNotification(transactionId: Int?) {
        guard isSignedIn else { return }
        if let transactionId { openedSessionId = transactionId }
        selectedTab = .charge
    }

    #if DEBUG
    func startDemo(signIn: Bool) async {
        isDemo = true
        let configuration = AppConfiguration(
            apiURL: URL(string: "https://demo.ev-app.invalid")!,
            portalURL: URL(string: "https://demo.ev-app.invalid")!,
            workspaceSlug: "demo",
            brandName: "Réseau Démo",
            supportEmail: "support@codetics.fr",
            privacyPolicyURL: URL(string: "https://codetics.fr")!
        )
        let session = URLSessionConfiguration.ephemeral
        session.protocolClasses = [DemoURLProtocol.self]
        launch = .checking
        account = .signedOut
        await connect(configuration, api: EVServerAPI(client: APIClient(baseURL: configuration.apiURL, configuration: session)))
        if signIn {
            try? await self.signIn(workspace: "demo", email: DemoData.email, password: DemoData.password)
        }
    }
    #endif
}
