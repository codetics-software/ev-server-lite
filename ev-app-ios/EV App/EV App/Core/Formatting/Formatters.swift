//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum Formatters {
    static func price(_ value: Decimal?, currency: String?) -> String {
        guard let value else { return "—" }
        return value.formatted(.currency(code: currency ?? "EUR").precision(.fractionLength(2...4)))
    }

    static func minor(_ value: Int?, currency: String) -> String {
        guard let value else { return "—" }
        return (Decimal(value) / 100).formatted(.currency(code: currency))
    }

    static func energy(_ kwh: Double?) -> String {
        guard let kwh else { return "—" }
        return Measurement(value: kwh, unit: UnitEnergy.kilowattHours)
            .formatted(.measurement(width: .abbreviated, usage: .asProvided, numberFormatStyle: .number.precision(.fractionLength(0...2))))
    }

    static func power(_ kw: Double?) -> String {
        guard let kw else { return "—" }
        return Measurement(value: kw, unit: UnitPower.kilowatts)
            .formatted(.measurement(width: .abbreviated, usage: .asProvided, numberFormatStyle: .number.precision(.fractionLength(0...1))))
    }

    static func duration(seconds: Int) -> String {
        Duration.seconds(seconds).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated, maximumUnitCount: 2))
    }

    static func distance(meters: Double) -> String {
        Measurement(value: meters, unit: UnitLength.meters)
            .formatted(.measurement(width: .abbreviated, usage: .road, numberFormatStyle: .number.precision(.fractionLength(0...1))))
    }
}
