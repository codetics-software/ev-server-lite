//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
@testable import EV_App

enum TestData {
    static let instant = Date(timeIntervalSince1970: 1_793_000_000)

    static func connector(
        _ id: Int = 1,
        status: String = "Available",
        type: String? = "T2",
        power: Double? = 22,
        transaction: Int? = nil,
        evseId: String? = nil
    ) -> ConnectorResponse {
        ConnectorResponse(
            connectorId: id, status: status, errorCode: "NoError", statusAt: instant, type: type, maximumPowerKw: power, phases: 3,
            currentPowerKw: nil, currentTransactionId: transaction, currentEnergyKwh: nil, currentSoc: nil, currentUserName: nil,
            inactiveSince: nil, evseId: evseId
        )
    }

    static func station(
        _ id: String = "PAR-01",
        name: String = "Gare de Lyon",
        connected: Bool = true,
        connectors: [ConnectorResponse] = [connector()],
        latitude: Double? = 48.8443,
        longitude: Double? = 2.3744,
        maximumPowerKw: Double? = nil,
        siteName: String? = nil,
        address: String? = nil,
        postalCode: String? = nil,
        city: String? = nil,
        publicEvses: [CustomerEvseLink] = [],
        tariff: CustomerTariff? = nil
    ) -> CustomerStationResponse {
        CustomerStationResponse(
            id: id, name: name, vendor: "Codetics", model: "Lite", connected: connected, lastSeen: instant, connectors: connectors,
            public: true, latitude: latitude, longitude: longitude, maximumPowerKw: maximumPowerKw, autoCharge: false, siteId: nil,
            siteAreaId: nil, siteName: siteName, address: address, postalCode: postalCode, city: city, country: "FR",
            publicEvses: publicEvses, photoCount: nil, tariff: tariff
        )
    }

    static func session(
        id: Int = 1,
        stationId: String = "PAR-01",
        connectorId: Int = 1,
        stoppedAt: Date? = nil,
        energy: Double? = 12.5,
        power: Double? = 11,
        soc: Int? = 64,
        price: Decimal? = 5.37,
        stopReason: String? = nil,
        signed: Bool = false
    ) -> ChargingSessionResponse {
        ChargingSessionResponse(
            id: id, stationId: stationId, connectorId: connectorId, startedAt: instant, stoppedAt: stoppedAt, energyKwh: energy,
            authorizationStatus: "Accepted", stopReason: stopReason, tokenUid: "04A1B2C3D4E580", userId: nil, userName: nil,
            siteAreaId: nil, siteId: nil, durationSeconds: 5_400, inactivitySeconds: 600, currentPowerKw: power,
            stateOfChargeStart: 40, stateOfCharge: soc, price: price, currency: "EUR", pricingDefinitionId: nil, billingStatus: "NONE",
            invoiceId: nil, roamingPartner: nil, publicPayment: false, softStopped: false, vehicleId: nil, autoCharge: false, vid: nil,
            errors: nil, signedMeterData: signed, signedMeterFormat: signed ? "OCMF" : nil
        )
    }

    static func token(
        _ uid: String,
        enabled: Bool = true,
        expires: Date? = nil,
        isDefault: Bool = false,
        vehicle: UUID? = nil
    ) -> ChargingTokenResponse {
        ChargingTokenResponse(
            uid: uid, userId: nil, type: "RFID", enabled: enabled, expiresAt: expires, visualId: "CARTE-\(uid.suffix(2))",
            description: nil, userName: nil, default: isDefault, lastUsedAt: nil, vehicleId: vehicle, vehicleName: nil, parentIdTag: nil
        )
    }

    static func fixture(_ name: String) throws -> Data {
        let directory = Bundle(for: TestBundleMarker.self).resourceURL!
        let nested = directory.appending(path: "Fixtures").appending(path: "\(name).json")
        let flat = directory.appending(path: "\(name).json")
        return try Data(contentsOf: FileManager.default.fileExists(atPath: nested.path()) ? nested : flat)
    }

    static func decodeFixture<T: Decodable>(_ type: T.Type, _ name: String) throws -> T {
        try JSONCoding.decoder.decode(T.self, from: fixture(name))
    }
}
