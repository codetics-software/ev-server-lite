//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct WorkspaceComponents: Decodable, Sendable, Equatable {
    let organization: Bool
    let pricing: Bool
    let billing: Bool
    let roaming: Bool
    let smartCharging: Bool
    let statistics: Bool
    let assets: Bool
    let cars: Bool
    let publicCharging: Bool
}
