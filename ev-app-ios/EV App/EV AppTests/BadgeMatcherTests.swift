//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct BadgeMatcherTests {
    private func token(_ uid: String, enabled: Bool = true, expires: Date? = nil) -> ChargingTokenResponse {
        ChargingTokenResponse(
            uid: uid, userId: UUID(), type: "RFID", enabled: enabled, expiresAt: expires, visualId: nil, description: nil,
            userName: "Thomas", default: false, lastUsedAt: nil, vehicleId: nil, vehicleName: nil, parentIdTag: nil
        )
    }

    @Test func matchesIgnoringCaseAndSeparators() {
        let owned = token("04a1b2c3d4e580")
        #expect(BadgeMatcher.match(uid: "04:A1:B2:C3:D4:E5:80", tokens: [owned]) == .registered(owned))
    }

    @Test func matchesReversedByteOrder() {
        let owned = token("80E5D4C3B2A104")
        #expect(BadgeMatcher.match(uid: "04A1B2C3D4E580", tokens: [owned]) == .registered(owned))
    }

    @Test func prefersExactMatch() {
        let exact = token("AABB")
        let reversed = token("BBAA")
        #expect(BadgeMatcher.match(uid: "AABB", tokens: [reversed, exact]) == .registered(exact))
    }

    @Test func reportsDisabledExpiredAndUnknown() {
        let disabled = token("AA", enabled: false)
        #expect(BadgeMatcher.match(uid: "AA", tokens: [disabled]) == .disabled(disabled))
        let expired = token("BB", expires: .distantPast)
        #expect(BadgeMatcher.match(uid: "BB", tokens: [expired]) == .expired(expired))
        #expect(BadgeMatcher.match(uid: "CC", tokens: [disabled]) == .notRegistered(uid: "CC"))
    }
}
