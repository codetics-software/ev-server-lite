//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct SignUpScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var acceptsTerms = false
    @State private var errorMessage: String?
    @State private var isDone = false
    @State private var isWorking = false

    private var canSubmit: Bool {
        !isWorking && !name.isEmpty && email.contains("@") && (16...128).contains(password.count) && acceptsTerms
    }

    var body: some View {
        NavigationStack {
            Form {
                if isDone {
                    Label("If sign-up is available for this address, a verification link has been sent. Open it, then sign in.", systemImage: "envelope.badge")
                } else {
                    Section {
                        TextField("Full name", text: $name).textContentType(.name)
                        TextField("Email", text: $email)
                            .textContentType(.username)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        SecureField("Password", text: $password).textContentType(.newPassword)
                    } footer: {
                        Text("Use between 16 and 128 characters. A sentence works well.")
                    }
                    Section {
                        Toggle("I accept the terms of use and the privacy policy", isOn: $acceptsTerms)
                        if let url = model.configuration?.privacyPolicyURL {
                            Link("Read the privacy policy", destination: url)
                        }
                    }
                    if let errorMessage {
                        ErrorBanner(message: errorMessage)
                    }
                }
            }
            .navigationTitle("Create an account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isDone ? "Close" : "Cancel") { dismiss() }
                }
                if !isDone {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Create") { Task { await submit() } }.disabled(!canSubmit)
                    }
                }
            }
        }
    }

    private func submit() async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await model.api?.signUp(SignupRequest(email: email.trimmingCharacters(in: .whitespaces), name: name, password: password))
            isDone = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
