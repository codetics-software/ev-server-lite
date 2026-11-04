//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct CustomerTariff: Decodable, Sendable, Hashable {
    let name: String
    let currency: String
    let flatPrice: Decimal?
    let energyPrice: Decimal?
    let timePrice: Decimal?
    let parkingPrice: Decimal?
}
