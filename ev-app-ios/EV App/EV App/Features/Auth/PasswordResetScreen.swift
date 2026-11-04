//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct PasswordResetScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State var workspace: String
    @State var email: String
    @State private var isDone = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                if isDone {
                    Label("If this account exists, a link to choose a new password has been sent. It expires after 30 minutes.", systemImage: "envelope.badge")
                } else {
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if let errorMessage {
                        ErrorBanner(message: errorMessage)
                    }
                }
            }
            .navigationTitle("Forgot password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isDone ? "Close" : "Cancel") { dismiss() }
                }
                if !isDone {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Send") { Task { await submit() } }.disabled(email.isEmpty)
                    }
                }
            }
        }
    }

    private func submit() async {
        do {
            try await model.api?.requestPasswordReset(ChallengeRequest(tenant: workspace, email: email))
            isDone = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
