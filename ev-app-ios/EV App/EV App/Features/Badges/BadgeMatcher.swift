//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum BadgeMatcher {
    enum Outcome: Equatable, Sendable {
        case registered(ChargingTokenResponse)
        case disabled(ChargingTokenResponse)
        case expired(ChargingTokenResponse)
        case notRegistered(uid: String)
    }

    static func normalize(_ uid: String) -> String {
        uid.uppercased().filter { $0.isHexDigit || $0.isLetter || $0.isNumber }
    }

    static func reversedBytes(_ uid: String) -> String {
        let normalized = normalize(uid)
        guard normalized.count.isMultiple(of: 2) else { return normalized }
        var bytes: [Substring] = []
        var index = normalized.startIndex
        while index < normalized.endIndex {
            let next = normalized.index(index, offsetBy: 2)
            bytes.append(normalized[index..<next])
            index = next
        }
        return bytes.reversed().joined()
    }

    static func match(uid: String, tokens: [ChargingTokenResponse], now: Date = .now) -> Outcome {
        let direct = normalize(uid)
        let reversed = reversedBytes(uid)
        guard let token = tokens.first(where: { normalize($0.uid) == direct }) ?? tokens.first(where: { normalize($0.uid) == reversed }) else {
            return .notRegistered(uid: direct)
        }
        if !token.enabled { return .disabled(token) }
        if let expiry = token.expiresAt, expiry <= now { return .expired(token) }
        return .registered(token)
    }
}
