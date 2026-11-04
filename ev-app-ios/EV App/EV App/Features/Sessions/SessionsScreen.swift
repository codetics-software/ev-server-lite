//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct SessionsScreen: View {
    @Environment(AppModel.self) private var model
    @State private var mode = Mode.history

    private enum Mode: Hashable {
        case history
        case statistics
    }

    var body: some View {
        NavigationStack {
            Group {
                switch mode {
                case .history: SessionHistoryView()
                case .statistics: StatisticsView()
                }
            }
            .navigationTitle("Sessions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if model.components?.statistics != false {
                    ToolbarItem(placement: .principal) {
                        Picker("View", selection: $mode) {
                            Text("History").tag(Mode.history)
                            Text("Statistics").tag(Mode.statistics)
                        }
                        .pickerStyle(.segmented)
                        .frame(width: 260)
                        .accessibilityIdentifier("sessions.mode")
                    }
                }
            }
            .navigationDestination(for: ChargingSessionResponse.self) { SessionDetailScreen(session: $0) }
        }
    }
}
