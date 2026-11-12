//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct ChargingPointIDTests {
    private let portal = URL(string: "https://reseau.fr")!

    private func stations() throws -> [CustomerStationResponse] {
        let json = #"""
        [{"id":"PAR-01","name":"Paris","connected":true,"connectors":[
            {"connector_id":1,"status":"Available","error_code":"NoError","status_at":"2026-11-01T10:00:00Z","evse_id":"FR*COD*EPAR011"},
            {"connector_id":2,"status":"Charging","error_code":"NoError","status_at":"2026-11-01T10:00:00Z","evse_id":"FR*COD*EPAR012"}],
          "public":true,"auto_charge":false,"public_evses":[]},
         {"id":"LYO-01","name":"Lyon","connected":true,"connectors":[
            {"connector_id":1,"status":"Available","error_code":"NoError","status_at":"2026-11-01T10:00:00Z"}],
          "public":true,"auto_charge":false,"public_evses":[]}]
        """#
        return try JSONCoding.decoder.decode([CustomerStationResponse].self, from: Data(json.utf8))
    }

    @Test func recognisesChargingPointIDs() {
        #expect(ChargingPointID.isValid("FR*COD*E12345*1"))
        #expect(ChargingPointID.isValid("frcode12345"))
        #expect(ChargingPointID.isValid("FR*COD*E PAR01 1"))
        #expect(!ChargingPointID.isValid("bonjour"))
        #expect(!ChargingPointID.isValid("https://reseau.fr"))
    }

    @Test func findsTheIDInsideAnOperatorLink() {
        #expect(ChargingPointID.find(in: "https://pay.example.com/start?evse=FR*ABC*E1234*1&lang=fr") == "FR*ABC*E1234*1")
        #expect(ChargingPointID.find(in: "https://pay.example.com/evse/fr%2Aabc%2Ae99") == "FR*ABC*E99")
        #expect(ChargingPointID.find(in: "https://pay.example.com/station/42") == nil)
    }

    @Test func matchesConnectorsIgnoringSeparatorsAndCase() throws {
        let all = try stations()
        let match = try #require(ChargingPointID.match("fr*cod*e par01*2", in: all))
        #expect(match.station.id == "PAR-01")
        #expect(match.connectorId == 2)
        #expect(ChargingPointID.match("FR*XYZ*EPAR011", in: all) == nil)
    }

    @Test func fallsBackToTheStationIdentifier() throws {
        let all = try stations()
        #expect(ChargingPointID.match("lyo01", in: all)?.connectorId == 1)
        #expect(ChargingPointID.match("par-01", in: all)?.connectorId == nil)
    }

    @Test func classifiesScannedCodes() throws {
        let evse = "5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10"
        #expect(try QRCodeParser.read("https://reseau.fr/charge/codetics/\(evse)", portalURL: portal).get() == .portal(PortalLink(workspace: "codetics", evseId: UUID(uuidString: evse)!)))
        #expect(try QRCodeParser.read(" FR*COD*E12345 ", portalURL: portal).get() == .chargingPoint("FR*COD*E12345"))
        let foreign = URL(string: "https://pay.example.com/s?id=FR*ABC*E77")!
        #expect(try QRCodeParser.read(foreign.absoluteString, portalURL: portal).get() == .operatorPortal(foreign, chargingPoint: "FR*ABC*E77"))
        #expect(QRCodeParser.read("ftp://pay.example.com/x", portalURL: portal) == .failure(.notALink))
        #expect(QRCodeParser.read("bonjour", portalURL: portal) == .failure(.notALink))
        #expect(QRCodeParser.read("https://reseau.fr/account", portalURL: portal) == .failure(.notAChargingPoint))
    }
}
