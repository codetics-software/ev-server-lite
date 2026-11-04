//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct SignInEntryScreen: View {
    @Environment(AppModel.self) private var model
    @State private var workspace = ""
    @State private var email = ""
    @State private var password = ""
    @State private var isWorking = false
    @State private var errorMessage: String?
    @State private var showsSignUp = false
    @State private var showsPasswordReset = false
    @State private var canResendVerification = false
    @State private var notice: String?

    private var fixedWorkspace: String? { model.configuration?.workspaceSlug }

    private var canSubmit: Bool {
        !isWorking && !email.isEmpty && !password.isEmpty && (fixedWorkspace != nil || !workspace.isEmpty)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(spacing: 12) {
                        Image(.appLogo)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 84, height: 84)
                            .clipShape(.rect(cornerRadius: 18))
                            .accessibilityHidden(true)
                        Text(model.brandName)
                            .font(.title2.bold())
                        Text("Sign in to see your badges, sessions and vehicles. You can still find stations and pay at a charging point without an account.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                }
                Section {
                    if fixedWorkspace == nil {
                        TextField("Workspace", text: $workspace)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("signin.email")
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                        .accessibilityIdentifier("signin.password")
                }
                if let errorMessage {
                    Section {
                        ErrorBanner(message: errorMessage)
                        if canResendVerification {
                            Button("Send the verification email again") { Task { await resend() } }
                        }
                    }
                }
                if let notice {
                    Section {
                        Label(notice, systemImage: "checkmark.circle")
                    }
                }
                Section {
                    Button {
                        Task { await signIn() }
                    } label: {
                        if isWorking {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Sign in").frame(maxWidth: .infinity).bold()
                        }
                    }
                    .disabled(!canSubmit)
                    .accessibilityIdentifier("signin.submit")
                    Button("Forgot password?") { showsPasswordReset = true }
                    if model.server?.signupEnabled == true {
                        Button("Create an account") { showsSignUp = true }
                    }
                }
                Section {
                    NavigationLink("About this app") { AboutScreen() }
                }
            }
            .navigationTitle("Account")
            .sheet(isPresented: $showsSignUp) { SignUpScreen() }
            .sheet(isPresented: $showsPasswordReset) {
                PasswordResetScreen(workspace: fixedWorkspace ?? workspace, email: email)
            }
        }
    }

    private func signIn() async {
        isWorking = true
        errorMessage = nil
        canResendVerification = false
        defer { isWorking = false }
        do {
            try await model.signIn(workspace: fixedWorkspace ?? workspace, email: email, password: password)
            password = ""
        } catch {
            errorMessage = error.localizedDescription
            canResendVerification = error.localizedDescription.contains("Vérifiez")
        }
    }

    private func resend() async {
        do {
            try await model.api?.resendVerification(ChallengeRequest(tenant: fixedWorkspace ?? workspace, email: email))
            notice = String(localized: "If this address needs verifying, an email has been sent.")
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
