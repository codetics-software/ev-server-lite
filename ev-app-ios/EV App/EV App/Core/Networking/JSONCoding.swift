//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum JSONCoding {
    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { container in
            let value = try container.singleValueContainer().decode(String.self)
            guard let date = parseInstant(value) else {
                throw DecodingError.dataCorrupted(
                    .init(codingPath: container.codingPath, debugDescription: "Invalid instant \(value)")
                )
            }
            return date
        }
        return decoder
    }()

    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()

    static func parseInstant(_ value: String) -> Date? {
        var text = value
        if let dot = text.firstIndex(of: "."), let zone = text.lastIndex(where: { $0 == "Z" || $0 == "+" }) {
            let fraction = text[text.index(after: dot)..<zone].prefix(3)
            text = String(text[..<dot]) + "." + fraction.padding(toLength: 3, withPad: "0", startingAt: 0) + String(text[zone...])
            return try? Date(text, strategy: Date.ISO8601FormatStyle(includingFractionalSeconds: true))
        }
        return try? Date(text, strategy: Date.ISO8601FormatStyle())
    }
}
