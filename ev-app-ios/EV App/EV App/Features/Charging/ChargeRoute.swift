//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum ChargeRoute: Hashable {
    case session(Int)
    case portal(PortalLink)
    case guest(GuestOrder)
    case chargingPointID(String?, operatorURL: URL?)
    case station(String, connectorId: Int?)
}
