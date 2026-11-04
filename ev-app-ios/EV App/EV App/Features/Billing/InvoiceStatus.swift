//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum InvoiceStatus {
    static func label(_ status: String) -> LocalizedStringResource {
        switch status {
        case "DRAFT": "Draft"
        case "OPEN": "To pay"
        case "PAID": "Paid"
        case "FAILED": "Failed"
        case "VOID": "Cancelled"
        case "UNCOLLECTIBLE": "Unpaid"
        default: "Unknown"
        }
    }
}
