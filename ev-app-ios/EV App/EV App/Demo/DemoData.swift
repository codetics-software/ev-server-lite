//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

#if DEBUG
import Foundation

nonisolated enum DemoData {
    static let email = "thomas.bernard@demo.codetics.fr"
    static let password = "demo-account-only-in-debug-builds"
    static let ownCard = "04A1B2C3D4E580"
    static let orderedCard = "04C7D8E9FA1B80"
    static let orderedCardNumber = "CARTE-2210"
    static let evseId = "5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10"
    static let portalLink = "https://demo.ev-app.invalid/charge/demo/\(evseId)"
    static let userId = "11111111-2222-4333-8444-555555555555"
    static let tenantId = "99999999-2222-4333-8444-555555555555"
}
#endif
