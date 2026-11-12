//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum ChargingPointID {
    struct Match: Equatable {
        let station: CustomerStationResponse
        let connectorId: Int?
    }

    static func normalize(_ value: String) -> String {
        value.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
    }

    static func isValid(_ value: String) -> Bool {
        let compact = value.uppercased().filter { !$0.isWhitespace }
        return compact.wholeMatch(of: /[A-Z]{2}\*?[A-Z0-9]{3}\*?E[A-Z0-9*]{1,40}/) != nil
    }

    static func find(in text: String) -> String? {
        let decoded = text.removingPercentEncoding ?? text
        guard let found = decoded.firstMatch(of: /(?i)(?:^|[^A-Z0-9])([A-Z]{2}\*[A-Z0-9]{3}\*E[A-Z0-9*]{1,40})/) else { return nil }
        return String(found.output.1).uppercased()
    }

    static func match(_ value: String, in stations: [CustomerStationResponse]) -> Match? {
        let wanted = normalize(value)
        guard !wanted.isEmpty else { return nil }
        for station in stations {
            if let connector = station.connectors.first(where: { $0.evseId.map(normalize) == wanted }) {
                return Match(station: station, connectorId: connector.connectorId)
            }
        }
        if let station = stations.first(where: { normalize($0.id) == wanted }) {
            return Match(station: station, connectorId: station.connectors.count == 1 ? station.connectors.first?.connectorId : nil)
        }
        return nil
    }
}
