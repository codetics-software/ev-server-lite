//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct PortalTariffRows: View {
    let tariff: ChargingTariff

    var body: some View {
        if tariff.energyPrice > 0 {
            LabeledContent("Energy", value: "\(Formatters.price(tariff.energyPrice, currency: tariff.currency)) / kWh")
        }
        if tariff.timePrice > 0 {
            LabeledContent("Charging time", value: "\(Formatters.price(tariff.timePrice, currency: tariff.currency)) / h")
        }
        if tariff.flatPrice > 0 {
            LabeledContent("Session fee", value: Formatters.price(tariff.flatPrice, currency: tariff.currency))
        }
    }
}
