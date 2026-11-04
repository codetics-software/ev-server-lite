//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct NotificationResponse: Decodable, Sendable, Identifiable {
    let id: UUID
    let type: String
    let userId: UUID?
    let recipient: String
    let subject: String
    let channel: String
    let createdAt: Date
    let sentAt: Date?
}
