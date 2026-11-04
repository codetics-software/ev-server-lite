//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct RecordInput<Payload: Encodable & Sendable>: Encodable, Sendable {
    let name: String
    let data: Payload
    let revision: Int?
}
