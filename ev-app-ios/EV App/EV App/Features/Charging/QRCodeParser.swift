//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum QRCodeParser {
    enum Failure: Error, Equatable {
        case notALink
        case foreignNetwork
        case notAChargingPoint
    }

    static func parse(_ value: String, portalURL: URL) -> Result<PortalLink, Failure> {
        guard
            let url = URL(string: value.trimmingCharacters(in: .whitespacesAndNewlines)),
            let scheme = url.scheme?.lowercased(),
            let host = url.host()?.lowercased()
        else {
            return .failure(.notALink)
        }
        guard scheme == portalURL.scheme?.lowercased(), host == portalURL.host()?.lowercased(), url.port == portalURL.port else {
            return .failure(.foreignNetwork)
        }
        let components = url.pathComponents.filter { $0 != "/" }
        guard
            components.count >= 3,
            let index = components.firstIndex(of: "charge"),
            components.count > index + 2,
            components[index + 1].wholeMatch(of: /[a-z0-9][a-z0-9-]{0,62}/) != nil,
            let evse = UUID(uuidString: components[index + 2])
        else {
            return .failure(.notAChargingPoint)
        }
        return .success(PortalLink(workspace: components[index + 1], evseId: evse))
    }

    static func message(for failure: Failure) -> String {
        switch failure {
        case .notALink:
            String(localized: "This QR code is not a charging point link.")
        case .foreignNetwork:
            String(localized: "This QR code does not belong to this charging network. For your safety, it was not opened.")
        case .notAChargingPoint:
            String(localized: "This link does not point to a charging point.")
        }
    }
}
