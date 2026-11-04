//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct InvoicesScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var invoices: [InvoiceResponse] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var presented: IdentifiableURL?

    var body: some View {
        List {
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
            ForEach(invoices) { invoice in
                Button {
                    Task { await open(invoice) }
                } label: {
                    InvoiceRow(invoice: invoice)
                }
                .buttonStyle(.plain)
            }
        }
        .overlay {
            if invoices.isEmpty, !isLoading, errorMessage == nil {
                ContentUnavailableView("No invoices", systemImage: "doc.text", description: Text("Your invoices will appear here."))
            }
        }
        .navigationTitle("Invoices")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $presented) { item in
            SafariView(url: item.url).ignoresSafeArea()
        }
        .task {
            defer { isLoading = false }
            do {
                invoices = try await model.api?.invoices().items ?? []
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func open(_ invoice: InvoiceResponse) async {
        do {
            let target: String?
            if invoice.status == "OPEN", let hosted = invoice.hostedUrl {
                target = hosted
            } else {
                target = try await model.api?.invoiceDownload(id: invoice.id).url
            }
            if let target, let url = URL(string: target), url.scheme == "https" {
                presented = IdentifiableURL(url: url)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
