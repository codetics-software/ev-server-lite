//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct ChargingSessionResponse: Decodable, Sendable, Identifiable, Hashable {
    let id: Int
    let stationId: String
    let connectorId: Int
    let startedAt: Date
    let stoppedAt: Date?
    let energyKwh: Double?
    let authorizationStatus: String
    let stopReason: String?
    let tokenUid: String?
    let userId: UUID?
    let userName: String?
    let siteAreaId: UUID?
    let siteId: UUID?
    let durationSeconds: Int
    let inactivitySeconds: Int
    let currentPowerKw: Double?
    let stateOfChargeStart: Int?
    let stateOfCharge: Int?
    let price: Decimal?
    let currency: String?
    let pricingDefinitionId: UUID?
    let billingStatus: String
    let invoiceId: UUID?
    let roamingPartner: String?
    let publicPayment: Bool
    let softStopped: Bool
    let vehicleId: UUID?
    let autoCharge: Bool
    let vid: String?
    let errors: [String]?
    let signedMeterData: Bool
    let signedMeterFormat: String?

    var isActive: Bool { stoppedAt == nil }
}
