//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ReportProblemScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let stationId: String?
    let connectorId: Int?
    @State var subject: String
    @State private var details = ""
    @State private var phone = ""
    @State private var isWorking = false
    @State private var isDone = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                if isDone {
                    Label("Thank you. The operator has been told about this problem.", systemImage: "checkmark.circle.fill")
                } else {
                    if let stationId {
                        LabeledContent("Station", value: stationId)
                    }
                    TextField("Subject", text: $subject)
                    TextField("What happened?", text: $details, axis: .vertical)
                        .lineLimit(4...10)
                    TextField("Phone (optional)", text: $phone)
                        .keyboardType(.phonePad)
                    if let errorMessage {
                        ErrorBanner(message: errorMessage)
                    }
                }
            }
            .navigationTitle("Report a problem")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isDone ? "Close" : "Cancel") { dismiss() }
                }
                if !isDone {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Send") { Task { await send() } }
                            .disabled(isWorking || subject.isEmpty || details.isEmpty)
                    }
                }
            }
        }
    }

    private func send() async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await model.api?.reportProblem(
                ErrorReportInput(subject: subject, description: details, stationId: stationId, connectorId: connectorId, phone: phone.nilIfEmpty)
            )
            isDone = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
