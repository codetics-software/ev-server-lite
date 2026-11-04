//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct MainTabView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        @Bindable var model = model
        TabView(selection: $model.selectedTab) {
            Tab("Map", systemImage: "map", value: AppTab.map) {
                StationMapScreen()
            }
            Tab("Charge", systemImage: "bolt.car", value: AppTab.charge) {
                ChargeScreen()
            }
            if model.isSignedIn {
                Tab("Sessions", systemImage: "clock.arrow.circlepath", value: AppTab.sessions) {
                    SessionsScreen()
                }
                Tab("Badges", systemImage: "wave.3.right.circle", value: AppTab.badges) {
                    BadgesScreen()
                }
            }
            Tab("Account", systemImage: "person.crop.circle", value: AppTab.account) {
                if model.isSignedIn {
                    AccountScreen()
                } else {
                    SignInEntryScreen()
                }
            }
        }
        .tabViewBottomAccessory(isEnabled: model.charging.current != nil) {
            if let session = model.charging.current {
                Button {
                    model.openedSessionId = session.id
                    model.selectedTab = .charge
                } label: {
                    ActiveSessionRow(session: session, station: model.stations.station(id: session.stationId))
                        .padding(.horizontal)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("session.accessory")
            }
        }
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            await model.charging.monitor()
        }
    }
}
