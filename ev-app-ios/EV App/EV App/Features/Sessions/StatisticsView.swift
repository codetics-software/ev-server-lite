//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Charts
import SwiftUI

struct StatisticsView: View {
    @Environment(AppModel.self) private var model
    @State private var metric = StatisticsMetric.consumption
    @State private var years: [Int] = []
    @State private var year = Calendar.current.component(.year, from: .now)
    @State private var result: StatisticsResponse?
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                Picker("Indicator", selection: $metric) {
                    ForEach(StatisticsMetric.allCases) { item in
                        Text(item.label).tag(item)
                    }
                }
                if !years.isEmpty {
                    Picker("Year", selection: $year) {
                        ForEach(years, id: \.self) { value in
                            Text(verbatim: String(value)).tag(value)
                        }
                    }
                }
            }
            if let result {
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(metric.format(result.total, currency: result.currency))")
                            .font(.title.bold().monospacedDigit())
                        Chart(Array(result.months.enumerated()), id: \.offset) { month, value in
                            BarMark(x: .value("Month", monthName(month)), y: .value("Value", value))
                                .foregroundStyle(Color.accentColor)
                        }
                        .frame(height: 210)
                    }
                }
                if result.groups.count > 1 {
                    Section("By station") {
                        ForEach(result.groups) { group in
                            LabeledContent(group.label, value: metric.format(group.total, currency: result.currency))
                        }
                    }
                }
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
        }
        .task { years = (try? await model.api?.statisticsYears()) ?? [] }
        .task(id: "\(metric.rawValue)-\(year)") { await load() }
        .refreshable { await load() }
    }

    private func monthName(_ index: Int) -> String {
        let symbols = Calendar.current.veryShortMonthSymbols
        return symbols.indices.contains(index) ? "\(symbols[index])\(String(repeating: "\u{200B}", count: index))" : "\(index + 1)"
    }

    private func load() async {
        do {
            result = try await model.api?.statistics(metric: metric.rawValue, year: year)
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
