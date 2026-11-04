//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct SessionHistoryView: View {
    @Environment(AppModel.self) private var model
    @State private var sessions: [ChargingSessionResponse] = []
    @State private var total = 0
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        List {
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
            ForEach(sessions) { session in
                NavigationLink(value: session) {
                    SessionRow(session: session, stationName: model.stations.station(id: session.stationId)?.name)
                }
                .onAppear {
                    if session.id == sessions.last?.id, sessions.count < total {
                        Task { await load(reset: false) }
                    }
                }
            }
        }
        .overlay {
            if sessions.isEmpty, !isLoading, errorMessage == nil {
                ContentUnavailableView("No sessions yet", systemImage: "bolt.car", description: Text("Your charging sessions will appear here."))
            }
        }
        .task { await load(reset: true) }
        .refreshable { await load(reset: true) }
    }

    private func load(reset: Bool) async {
        guard !isLoading, let api = model.api else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let page = try await api.sessions(offset: reset ? 0 : sessions.count, limit: 30)
            sessions = reset ? page.items : sessions + page.items
            total = page.total
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch APIError.unauthorized {
            model.sessionExpired()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
