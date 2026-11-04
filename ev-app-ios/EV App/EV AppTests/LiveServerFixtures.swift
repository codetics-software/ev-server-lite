//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
@testable import EV_App

enum LiveServerFixtures {
    static let names = ["configuration", "signin", "workspace", "account", "tokens", "stations", "station", "public_stations", "photos", "cars", "catalog", "sessions", "session", "consumptions", "notifications", "types", "preferences", "invoices", "statistics", "portal", "guest", "sites", "eula", "csrf", "autocharge", "receipt", "card_offer", "card_orders", "card_activated"]

    private static let directory = Bundle(for: TestBundleMarker.self).resourceURL!

    static func decode(_ name: String) throws {
        let url = directory.appending(path: "Fixtures").appending(path: "\(name).json")
        let fallback = directory.appending(path: "\(name).json")
        let data = try Data(contentsOf: FileManager.default.fileExists(atPath: url.path()) ? url : fallback)
        let decoder = JSONCoding.decoder
        switch name {
        case "configuration": _ = try decoder.decode(MobileConfigurationResponse.self, from: data)
        case "signin": _ = try decoder.decode(AppSessionResponse.self, from: data)
        case "workspace": _ = try decoder.decode(WorkspaceResponse.self, from: data)
        case "account": _ = try decoder.decode(UserResponse.self, from: data)
        case "tokens": _ = try decoder.decode([ChargingTokenResponse].self, from: data)
        case "stations", "public_stations": _ = try decoder.decode([CustomerStationResponse].self, from: data)
        case "station": _ = try decoder.decode(CustomerStationResponse.self, from: data)
        case "photos": _ = try decoder.decode([StationPhotoResponse].self, from: data)
        case "cars": _ = try decoder.decode([RecordResponse<CarData>].self, from: data)
        case "catalog": _ = try decoder.decode(PageResponse<RecordResponse<CarCatalogData>>.self, from: data)
        case "sessions": _ = try decoder.decode(PageResponse<ChargingSessionResponse>.self, from: data)
        case "session": _ = try decoder.decode(ChargingSessionResponse.self, from: data)
        case "consumptions": _ = try decoder.decode([ConsumptionPoint].self, from: data)
        case "notifications": _ = try decoder.decode(PageResponse<NotificationResponse>.self, from: data)
        case "types": _ = try decoder.decode([NotificationTypeInfo].self, from: data)
        case "preferences": _ = try decoder.decode(NotificationPreferences.self, from: data)
        case "invoices": _ = try decoder.decode(PageResponse<InvoiceResponse>.self, from: data)
        case "statistics": _ = try decoder.decode(StatisticsResponse.self, from: data)
        case "portal": _ = try decoder.decode(PortalEvseResponse.self, from: data)
        case "guest": _ = try decoder.decode(PortalPaymentResponse.self, from: data)
        case "sites": _ = try decoder.decode([UserSiteResponse].self, from: data)
        case "eula": _ = try decoder.decode(EulaResponse.self, from: data)
        case "csrf": _ = try decoder.decode(CsrfResponse.self, from: data)
        case "autocharge": _ = try decoder.decode([AutoChargeCandidate].self, from: data)
        case "receipt": _ = try decoder.decode(CommandReceipt.self, from: data)
        case "card_offer": _ = try decoder.decode(CardOfferResponse.self, from: data)
        case "card_orders": _ = try decoder.decode([CardOrderResponse].self, from: data)
        case "card_activated": _ = try decoder.decode(ChargingTokenResponse.self, from: data)
        default: fatalError(name)
        }
    }
}
