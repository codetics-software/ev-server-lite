//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct TariffSection: View {
    let tariff: CustomerTariff

    var body: some View {
        Section("Price") {
            Text(tariff.name).font(.subheadline.weight(.medium))
            if let energy = tariff.energyPrice, energy > 0 {
                LabeledContent("Energy", value: "\(Formatters.price(energy, currency: tariff.currency)) / kWh")
            }
            if let time = tariff.timePrice, time > 0 {
                LabeledContent("Charging time", value: "\(Formatters.price(time, currency: tariff.currency)) / h")
            }
            if let parking = tariff.parkingPrice, parking > 0 {
                LabeledContent("Parking after charging", value: "\(Formatters.price(parking, currency: tariff.currency)) / h")
            }
            if let flat = tariff.flatPrice, flat > 0 {
                LabeledContent("Session fee", value: Formatters.price(flat, currency: tariff.currency))
            }
        }
    }
}
