//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct MobileConfigurationResponse: Decodable, Sendable, Equatable {
    let server: String
    let apiVersion: Int
    let publicUrl: String
    let signupEnabled: Bool
    let publicTenant: String?
    let pushEnabled: Bool
    let walletEnabled: Bool
    let walletNfcEnabled: Bool
}
