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

    enum Scan: Equatable {
        case portal(PortalLink)
        case chargingPoint(String)
        case operatorPortal(URL, chargingPoint: String?)
    }

    static func read(_ value: String, portalURL: URL) -> Result<Scan, Failure> {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if ChargingPointID.isValid(trimmed) {
            return .success(.chargingPoint(trimmed.uppercased().filter { !$0.isWhitespace }))
        }
        switch parse(trimmed, portalURL: portalURL) {
        case .success(let link):
            return .success(.portal(link))
        case .failure(.foreignNetwork):
            guard let url = URL(string: trimmed), let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http" else {
                return .failure(.notALink)
            }
            return .success(.operatorPortal(url, chargingPoint: ChargingPointID.find(in: trimmed)))
        case .failure(let failure):
            return .failure(failure)
        }
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
