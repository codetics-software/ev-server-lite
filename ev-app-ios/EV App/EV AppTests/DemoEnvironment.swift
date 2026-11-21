//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import ObjectiveC
import Testing
import UserNotifications
@testable import EV_App

@Suite(.serialized)
struct DemoBackedTests {}

@MainActor
enum DemoEnvironment {
    static let baseURL = URL(string: "https://demo.ev-app.invalid")!

    static func api() -> EVServerAPI {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [DemoURLProtocol.self]
        return EVServerAPI(client: APIClient(baseURL: baseURL, configuration: configuration))
    }

    static func signedInAPI() async throws -> EVServerAPI {
        let api = api()
        let session = try await api.signIn(LoginRequest(tenant: "demo", email: DemoData.email, password: DemoData.password))
        api.client.bearerToken = session.token
        return api
    }

    @discardableResult
    static func signedIn() async -> AppModel {
        NotificationPermissionStub.install()
        let model = AppModel.shared
        if !(model.isDemo && model.isSignedIn && model.launch == .ready) {
            await model.startDemo(signIn: true)
        }
        model.selectedTab = .map
        return model
    }

    @discardableResult
    static func guest() async -> AppModel {
        NotificationPermissionStub.install()
        let model = AppModel.shared
        if model.isSignedIn { await model.signOut() }
        if !(model.isDemo && model.launch == .ready) {
            await model.startDemo(signIn: false)
        }
        await model.stations.reload()
        model.selectedTab = .map
        return model
    }

    static func stopLiveSessions(_ api: EVServerAPI) async throws {
        for session in try await api.sessions(status: "active").items {
            _ = try await api.stopSession(id: session.id)
        }
    }
}

enum NotificationPermissionStub {
    private nonisolated(unsafe) static var installed = false

    static func install() {
        guard !installed else { return }
        installed = true
        let selector = NSSelectorFromString("requestAuthorizationWithOptions:completionHandler:")
        guard let method = class_getInstanceMethod(UNUserNotificationCenter.self, selector) else { return }
        let replacement: @convention(block) (AnyObject, UInt, @escaping (Bool, (any Error)?) -> Void) -> Void = { _, _, completion in
            completion(false, nil)
        }
        method_setImplementation(method, imp_implementationWithBlock(replacement))
    }
}
