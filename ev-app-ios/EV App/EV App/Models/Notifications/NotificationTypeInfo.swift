//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct NotificationTypeInfo: Decodable, Sendable, Identifiable {
    let type: String
    let label: String
    let audience: String

    var id: String { type }
}
