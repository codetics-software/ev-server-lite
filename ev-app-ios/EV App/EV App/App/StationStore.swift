//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Observation

@Observable
final class StationStore {
    private(set) var stations: [CustomerStationResponse] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    func reload() async {
        let model = AppModel.shared
        guard let api = model.api else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            if model.isSignedIn {
                stations = try await api.stations()
            } else if let slug = model.configuration?.workspaceSlug {
                stations = try await api.publicStations(workspace: slug)
            }
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch APIError.unauthorized {
            model.sessionExpired()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func station(id: String) -> CustomerStationResponse? {
        stations.first { $0.id == id }
    }
}
