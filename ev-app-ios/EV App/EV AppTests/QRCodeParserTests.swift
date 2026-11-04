//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct QRCodeParserTests {
    private let portal = URL(string: "https://reseau.fr")!
    private let evse = "5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10"

    @Test func parsesPortalLink() throws {
        let link = try QRCodeParser.parse("https://reseau.fr/charge/codetics/\(evse)", portalURL: portal).get()
        #expect(link.workspace == "codetics")
        #expect(link.evseId == UUID(uuidString: evse))
    }

    @Test func rejectsAnotherHost() {
        #expect(QRCodeParser.parse("https://evil.example/charge/codetics/\(evse)", portalURL: portal) == .failure(.foreignNetwork))
        #expect(QRCodeParser.parse("https://reseau.fr.evil.example/charge/codetics/\(evse)", portalURL: portal) == .failure(.foreignNetwork))
        #expect(QRCodeParser.parse("http://reseau.fr/charge/codetics/\(evse)", portalURL: portal) == .failure(.foreignNetwork))
    }

    @Test func rejectsOtherContent() {
        #expect(QRCodeParser.parse("bonjour", portalURL: portal) == .failure(.notALink))
        #expect(QRCodeParser.parse("https://reseau.fr/account", portalURL: portal) == .failure(.notAChargingPoint))
        #expect(QRCodeParser.parse("https://reseau.fr/charge/codetics/not-a-uuid", portalURL: portal) == .failure(.notAChargingPoint))
        #expect(QRCodeParser.parse("https://reseau.fr/charge/../\(evse)", portalURL: portal) == .failure(.notAChargingPoint))
    }
}
