//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Observation

@Observable
final class GuestOrderStore {
    private(set) var orders: [GuestOrder] = []
    private let keychain = KeychainStore(service: "fr.codetics.evapp.guest")

    init() {
        if let data = keychain.read("orders"), let stored = try? JSONDecoder().decode([GuestOrder].self, from: data) {
            orders = stored
        }
    }

    func add(_ order: GuestOrder) {
        orders.removeAll { $0.id == order.id }
        orders.insert(order, at: 0)
        persist()
    }

    func remove(_ id: UUID) {
        orders.removeAll { $0.id == id }
        persist()
    }

    func refresh() async {
        guard let api = AppModel.shared.api else { return }
        for order in orders {
            do {
                let payment = try await api.guestSession(id: order.id, accessToken: order.accessToken)
                if payment.state.isFinished, order.createdAt < .now.addingTimeInterval(-86_400) {
                    remove(order.id)
                }
            } catch APIError.server(status: 404, _, _) {
                remove(order.id)
            } catch {
                continue
            }
        }
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(orders) {
            keychain.write(data, account: "orders")
        }
    }
}
