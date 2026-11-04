//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum UserRole: String, Codable, Sendable {
    case admin = "ADMIN"
    case `operator` = "OPERATOR"
    case viewer = "VIEWER"
    case driver = "DRIVER"
}
