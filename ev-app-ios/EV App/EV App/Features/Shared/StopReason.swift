//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum StopReason {
    static func label(for value: String) -> String {
        switch value {
        case "Local": String(localized: "Stopped at the station")
        case "Remote": String(localized: "Stopped remotely")
        case "EVDisconnected": String(localized: "Vehicle unplugged")
        case "EmergencyStop": String(localized: "Emergency stop")
        case "PowerLoss": String(localized: "Power outage")
        case "HardReset", "SoftReset", "Reboot", "ImmediateReset": String(localized: "Station restarted")
        case "UnlockCommand": String(localized: "Connector unlocked remotely")
        case "DeAuthorized": String(localized: "Authorization withdrawn")
        case "StoppedByEV": String(localized: "Stopped by the vehicle")
        case "SOCLimitReached": String(localized: "Battery limit reached")
        case "TimeLimitReached": String(localized: "Time limit reached")
        case "EnergyLimitReached": String(localized: "Energy limit reached")
        case "Timeout": String(localized: "Timed out")
        case "MasterPass": String(localized: "Stopped with a master pass")
        case "LocalOutOfCredit": String(localized: "Credit exhausted")
        case "GroundFault": String(localized: "Ground fault")
        case "OvercurrentFault": String(localized: "Overcurrent")
        case "PowerQuality": String(localized: "Power quality issue")
        case "StaleSession": String(localized: "Closed without news from the station")
        case "SoftStop": String(localized: "Closed by the operator")
        case "Other": String(localized: "Other reason")
        default: value
        }
    }
}
