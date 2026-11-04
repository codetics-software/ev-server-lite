//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum VehicleEditorTarget: Identifiable {
    case new
    case edit(RecordResponse<CarData>)

    var id: String {
        switch self {
        case .new: "new"
        case .edit(let car): car.id.uuidString
        }
    }
}
