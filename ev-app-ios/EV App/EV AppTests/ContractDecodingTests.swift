//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct ContractDecodingTests {
    private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try JSONCoding.decoder.decode(T.self, from: Data(json.utf8))
    }

    @Test func parsesServerInstants() {
        #expect(JSONCoding.parseInstant("2026-11-04T08:55:28.536984Z") != nil)
        #expect(JSONCoding.parseInstant("2026-11-04T08:55:28Z") != nil)
        #expect(JSONCoding.parseInstant("2026-11-04T08:55:28.5Z") != nil)
        #expect(JSONCoding.parseInstant("nope") == nil)
    }

    @Test func keepsNotificationTypeKeys() throws {
        let preferences = try decode(NotificationPreferences.self, #"{"email":true,"push":false,"types":{"SESSION_STARTED":true,"END_OF_CHARGE":false}}"#)
        #expect(preferences.types["END_OF_CHARGE"] == false)
        #expect(preferences.types["SESSION_STARTED"] == true)
    }

    @Test func encodesInputsInServerNaming() throws {
        let data = try JSONCoding.encoder.encode(SessionStartInput(stationId: "A", connectorId: 1, tokenUid: "B"))
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        #expect(Set(object.keys) == ["stationId", "connectorId", "tokenUid"])
        let car = CarData(licensePlate: "AA-123-BB", batteryKwh: 60)
        let carObject = try #require(JSONSerialization.jsonObject(with: JSONCoding.encoder.encode(RecordInput(name: "x", data: car, revision: nil))) as? [String: Any])
        let carData = try #require(carObject["data"] as? [String: Any])
        #expect(carData["licensePlate"] as? String == "AA-123-BB")
        #expect(carData["vin"] == nil)
    }

    @Test(arguments: LiveServerFixtures.names)
    func decodesRecordedServerResponses(name: String) throws {
        try LiveServerFixtures.decode(name)
    }
}
