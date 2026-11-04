//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct NotificationPreferences: Codable, Sendable, Equatable {
    var email: Bool
    var push: Bool
    var types: [String: Bool]
}
