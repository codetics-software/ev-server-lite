//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ProfileScreen: View {
    @Environment(AppModel.self) private var model
    @State private var name = ""
    @State private var phone = ""
    @State private var locale = "fr"
    @State private var email = ""
    @State private var loaded = false
    @State private var errorMessage: String?
    @State private var notice: String?

    private let locales: [(String, LocalizedStringResource)] = [
        ("fr", "French"), ("en", "English"), ("de", "German"), ("es", "Spanish"), ("it", "Italian"), ("pt", "Portuguese"), ("cs", "Czech"),
    ]

    var body: some View {
        Form {
            Section {
                TextField("Full name", text: $name).textContentType(.name)
                LabeledContent("Email", value: email)
                TextField("Phone", text: $phone)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                Picker("Language", selection: $locale) {
                    ForEach(locales, id: \.0) { code, label in
                        Text(label).tag(code)
                    }
                }
            }
            Section {
                Button("Save") { Task { await save() } }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                Button("Change my password") { Task { await requestPassword() } }
            }
            if let notice {
                Label(notice, systemImage: "checkmark.circle")
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
        }
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard !loaded, let profile = try? await model.api?.profile() else { return }
            name = profile.displayName
            phone = profile.phone ?? ""
            locale = profile.locale
            email = profile.email
            loaded = true
        }
    }

    private func save() async {
        do {
            _ = try await model.api?.updateProfile(ProfileInput(name: name.trimmingCharacters(in: .whitespaces), phone: phone.nilIfEmpty, locale: locale))
            await model.refreshPrincipal()
            notice = String(localized: "Profile saved.")
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func requestPassword() async {
        guard let slug = model.workspace?.slug else { return }
        do {
            try await model.api?.requestPasswordReset(ChallengeRequest(tenant: slug, email: email))
            notice = String(localized: "A link to choose a new password has been sent to your email address.")
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
