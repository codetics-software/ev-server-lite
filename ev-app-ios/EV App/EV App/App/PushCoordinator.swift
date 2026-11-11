//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Observation
import UIKit
import UserNotifications

@Observable
final class PushCoordinator {
    private(set) var authorization: UNAuthorizationStatus = .notDetermined
    private(set) var deviceToken: String?
    private(set) var lastError: String?

    private var environment: String {
        #if DEBUG
        "sandbox"
        #else
        "production"
        #endif
    }

    func requestAuthorization() async {
        let model = AppModel.shared
        guard model.server?.pushEnabled == true else { return }
        let center = UNUserNotificationCenter.current()
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        authorization = await center.notificationSettings().authorizationStatus
        guard authorization == .authorized || authorization == .provisional else { return }
        if !model.isDemo { UIApplication.shared.registerForRemoteNotifications() }
    }

    func deviceRegistered(token: String) async {
        deviceToken = token
        await sendToken()
    }

    func registrationFailed(_ error: any Error) {
        lastError = error.localizedDescription
    }

    private func sendToken() async {
        let model = AppModel.shared
        guard let api = model.api, model.isSignedIn, let deviceToken else { return }
        do {
            try await api.registerDevice(
                PushDeviceInput(token: deviceToken, platform: "IOS", environment: environment, locale: AppLanguage.current)
            )
            lastError = nil
        } catch {
            lastError = error.localizedDescription
        }
    }

    func unregister() async {
        guard let api = AppModel.shared.api, let deviceToken else { return }
        try? await api.unregisterDevice(token: deviceToken)
    }
}
