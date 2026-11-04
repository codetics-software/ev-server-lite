//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum BillingStatus {
    static func label(_ status: String) -> LocalizedStringResource {
        switch status {
        case "NONE": "Not billed"
        case "PENDING": "Pending"
        case "INVOICED": "Invoiced"
        case "PAID": "Paid"
        case "FAILED": "Payment failed"
        default: "Unknown"
        }
    }
}
