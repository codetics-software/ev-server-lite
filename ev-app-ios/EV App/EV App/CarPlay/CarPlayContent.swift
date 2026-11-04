//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CarPlay

enum CarPlayContent {
    static func session(_ model: AppModel) -> (items: [CPInformationItem], isActive: Bool) {
        guard model.isSignedIn else {
            return ([CPInformationItem(title: String(localized: "Not signed in"), detail: String(localized: "Sign in on your iPhone to follow your charging sessions."))], false)
        }
        guard let session = model.charging.current else {
            return ([CPInformationItem(title: String(localized: "No charging in progress"), detail: String(localized: "Your session appears here once it starts."))], false)
        }
        var items = [
            CPInformationItem(title: String(localized: "Station"), detail: model.stations.station(id: session.stationId)?.name ?? session.stationId),
            CPInformationItem(title: String(localized: "Power"), detail: Formatters.power(session.currentPowerKw ?? 0)),
            CPInformationItem(title: String(localized: "Energy"), detail: Formatters.energy(session.energyKwh)),
            CPInformationItem(title: String(localized: "Duration"), detail: Formatters.duration(seconds: session.durationSeconds)),
        ]
        if let soc = session.stateOfCharge {
            items.append(CPInformationItem(title: String(localized: "Battery"), detail: "\(soc)%"))
        }
        if session.price != nil {
            items.append(CPInformationItem(title: String(localized: "Estimated price"), detail: Formatters.price(session.price, currency: session.currency)))
        }
        return (items, true)
    }
}
