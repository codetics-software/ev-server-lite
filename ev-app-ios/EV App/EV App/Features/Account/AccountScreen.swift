//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct AccountScreen: View {
    @Environment(AppModel.self) private var model
    @State private var confirmsSignOutEverywhere = false
    @State private var confirmsDeletion = false
    @State private var errorMessage: String?
    @State private var showsReport = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink {
                        ProfileScreen()
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "person.crop.circle.fill")
                                .font(.largeTitle)
                                .foregroundStyle(Color.accentColor)
                                .accessibilityHidden(true)
                            VStack(alignment: .leading) {
                                Text(model.principal?.name ?? "").font(.headline)
                                Text(model.principal?.email ?? "").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .accessibilityIdentifier("account.profile")
                }
                Section {
                    if model.components?.cars != false {
                        NavigationLink {
                            VehiclesScreen()
                        } label: {
                            Label("My vehicles", systemImage: "car.fill")
                        }
                        .accessibilityIdentifier("account.vehicles")
                    }
                    if model.components?.billing != false {
                        NavigationLink {
                            PaymentMethodsScreen()
                        } label: {
                            Label("Payment methods", systemImage: "creditcard")
                        }
                        NavigationLink {
                            InvoicesScreen()
                        } label: {
                            Label("Invoices", systemImage: "doc.text")
                        }
                        .accessibilityIdentifier("account.invoices")
                    }
                    NavigationLink {
                        NotificationsScreen()
                    } label: {
                        Label("Notifications", systemImage: "bell")
                    }
                    .accessibilityIdentifier("account.notifications")
                    NavigationLink {
                        MySitesScreen()
                    } label: {
                        Label("My sites", systemImage: "building.2")
                    }
                }
                Section {
                    Button("Report a problem", systemImage: "exclamationmark.bubble") { showsReport = true }
                    NavigationLink {
                        AboutScreen()
                    } label: {
                        Label("About", systemImage: "info.circle")
                    }
                    .accessibilityIdentifier("account.about")
                }
                Section {
                    Button("Sign out") { Task { await model.signOut() } }
                    Button("Sign out from all devices") { confirmsSignOutEverywhere = true }
                        .confirmationDialog("Sign out from all your devices?", isPresented: $confirmsSignOutEverywhere, titleVisibility: .visible) {
                            Button("Sign out everywhere", role: .destructive) { Task { await model.signOut(everywhere: true) } }
                        }
                }
                Section {
                    if model.principal?.role != .admin {
                        Button("Delete my account", role: .destructive) { confirmsDeletion = true }
                            .accessibilityIdentifier("account.delete")
                            .confirmationDialog(
                                "Delete your account permanently?",
                                isPresented: $confirmsDeletion,
                                titleVisibility: .visible
                            ) {
                                Button("Delete my account", role: .destructive) { Task { await deleteAccount() } }
                            } message: {
                                Text("Your profile, vehicles, photos and device registrations are removed and your badges are disabled. Sessions and invoices kept for legal reasons no longer identify you.")
                            }
                    }
                    if let errorMessage {
                        ErrorBanner(message: errorMessage)
                    }
                }
            }
            .navigationTitle("Account")
            .sheet(isPresented: $showsReport) {
                ReportProblemScreen(stationId: nil, connectorId: nil, subject: "")
            }
        }
    }

    private func deleteAccount() async {
        do {
            try await model.deleteAccount()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
