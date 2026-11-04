//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct AppConfiguration: Sendable, Equatable {
    let apiURL: URL
    let portalURL: URL
    let workspaceSlug: String
    let brandName: String
    let supportEmail: String
    let privacyPolicyURL: URL
}
