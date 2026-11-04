//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import PassKit

struct IdentifiablePass: Identifiable {
    let pass: PKPass

    var id: String { pass.serialNumber }
}
