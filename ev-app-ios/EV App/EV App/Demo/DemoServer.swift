//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

#if DEBUG
import Foundation

nonisolated final class DemoServer: @unchecked Sendable {
    static let shared = DemoServer()

    private struct Live {
        let id: Int
        let station: String
        let connector: Int
        let token: String
        let startedAt: Date
    }

    private struct Guest {
        let id: String
        let credit: Int
        let createdAt: Date
        var stoppedAt: Date?
    }

    private struct Photo {
        let id: String
        let station: String
        let data: Data
        let mine: Bool
        let createdAt: Date
    }

    private let lock = NSLock()
    private let iso = ISO8601DateFormatter()
    private var live: Live?
    private var finished: [[String: Any]] = []
    private var cars: [[String: Any]]
    private var defaultCar: String?
    private var preferences: [String: Any] = ["email": true, "push": true, "types": [String: Bool]()]
    private var guests: [String: Guest] = [:]
    private var photos: [Photo] = []
    private var nextSession = 2150
    private var eulaAccepted = true
    private var cardActivated = false

    private init() {
        let car = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        defaultCar = car
        cars = [[
            "id": car,
            "name": "Renault Megane E-Tech",
            "revision": 1,
            "updated_at": "2026-10-20T08:00:00Z",
            "data": [
                "userId": DemoData.userId,
                "make": "Renault",
                "model": "Megane E-Tech EV60",
                "licensePlate": "GH-482-TZ",
                "batteryKwh": 60,
                "maxAcPowerKw": 22,
                "maxDcPowerKw": 130,
                "default": true,
            ] as [String: Any],
        ]]
        finished = (0..<9).map { makeHistory(index: $0) }
    }

    private func date(_ date: Date) -> String { iso.string(from: date) }

    private func nullable(_ value: (some Any)?) -> Any { value.map { $0 as Any } ?? NSNull() }

    private func json(_ value: Any, status: Int = 200) -> (Int, Data, String) {
        (status, (try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed])) ?? Data(), "application/json")
    }

    private func problem(_ status: Int, _ detail: String) -> (Int, Data, String) {
        json(["status": status, "title": "Error", "detail": detail], status: status)
    }

    private func decode(_ body: Data?) -> [String: Any] {
        body.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
    }

    func handle(_ request: URLRequest, body: Data?) -> (Int, Data, String) {
        lock.lock()
        defer { lock.unlock() }
        let method = request.httpMethod ?? "GET"
        let path = request.url?.path() ?? ""
        let parts = path.split(separator: "/").map(String.init)
        let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func parameter(_ name: String) -> String? { query.first { $0.name == name }?.value }
        guard parts.count >= 2 else { return problem(404, "Not found") }
        let route = parts.dropFirst(2).joined(separator: "/")

        switch (method, route) {
        case ("GET", "public/mobile/configuration"):
            return json([
                "server": "ev-supervision-lite", "api_version": 1, "public_url": "https://demo.ev-app.invalid",
                "signup_enabled": true, "public_tenant": "demo", "push_enabled": true, "wallet_enabled": true, "wallet_nfc_enabled": true,
            ])
        case ("GET", "auth/csrf"):
            return json(["token": "demo", "headerName": "X-XSRF-TOKEN"])
        case ("POST", "auth/app/signin"):
            let input = decode(body)
            guard input["email"] as? String == DemoData.email, input["password"] as? String == DemoData.password else {
                return problem(401, "Identifiants incorrects.")
            }
            return json(["token": "demo-token-demo-token-demo-token-demo-token", "expires_at": date(.now.addingTimeInterval(2_592_000)), "user": principal()])
        case ("GET", "auth/me"):
            return json(principal())
        case ("POST", "auth/signout"), ("POST", "auth/signout/all"), ("POST", "auth/eula/accept"), ("POST", "auth/signon"), ("POST", "auth/password/reset/request"), ("POST", "auth/mail/resend"):
            return json(["success": true, "accepted": true])
        case ("GET", "auth/eula"):
            return json(["version": "1.0", "attribution": "Supervision de recharge Lite — Kevin Vavelin, Codetics", "url": "https://codetics.fr"])
        case ("GET", "workspace"):
            return json([
                "id": DemoData.tenantId, "slug": "demo", "name": "Réseau Démo", "active": true, "logo_url": NSNull(),
                "components": [
                    "organization": true, "pricing": true, "billing": true, "roaming": false, "smart_charging": false,
                    "statistics": true, "assets": false, "cars": true, "public_charging": true,
                ],
            ])
        case ("GET", "account"):
            return json([
                "id": DemoData.userId, "email": DemoData.email, "display_name": "Thomas Bernard", "role": "DRIVER", "enabled": true,
                "email_verified": true, "phone": "+33 6 12 34 56 78", "locale": "fr", "created_at": "2025-03-12T10:00:00Z",
                "last_login_at": date(.now), "billing_customer": true, "default_car_id": nullable(defaultCar), "site_count": 1, "eula_accepted": eulaAccepted,
            ])
        case ("PUT", "account"):
            return handle(URLRequest(url: request.url!), body: nil)
        case ("DELETE", "account"):
            return json(["success": true])
        case ("GET", "account/sites"):
            return json([["site_id": "c0000000-0000-4000-8000-000000000001", "name": "Parking privé Codetics", "site_admin": false, "site_owner": false]])
        case ("GET", "account/tokens"):
            return json(tokens())
        case ("GET", "account/card-offer"):
            return json(["available": true, "price_minor": 1500, "currency": "EUR", "description": "Carte RFID du réseau", "shipping": true])
        case ("GET", "account/card-orders"):
            return json([cardOrder()])
        case ("POST", "account/card-orders/activate"):
            let number = (decode(body)["number"] as? String ?? "").uppercased().filter { $0.isLetter || $0.isNumber }
            let accepted = [DemoData.orderedCard, DemoData.orderedCardNumber].map { $0.filter { $0.isLetter || $0.isNumber } }
            guard !cardActivated, accepted.contains(number) else {
                return problem(404, "Aucune carte commandée sur votre compte ne correspond à ce numéro.")
            }
            cardActivated = true
            return json(orderedCardToken())
        case ("GET", "account/stations"):
            return json(stations(signedIn: true))
        case ("GET", "public/workspaces/demo/stations"):
            return json(stations(signedIn: false))
        case ("GET", "account/cars"):
            return json(cars)
        case ("POST", "account/cars"):
            let input = decode(body)
            let id = UUID().uuidString.lowercased()
            var data = input["data"] as? [String: Any] ?? [:]
            data["userId"] = DemoData.userId
            if data["default"] as? Bool == true { defaultCar = id }
            cars.append(["id": id, "name": input["name"] ?? "Véhicule", "revision": 0, "updated_at": date(.now), "data": data])
            return json(["id": id, "revision": 0])
        case ("GET", "account/car-catalog"):
            return json(catalog(search: parameter("search") ?? ""))
        case ("GET", "account/autocharge/candidates"):
            return json([])
        case ("GET", "account/notifications"):
            return json(normalizedPreferences())
        case ("PUT", "account/notifications"):
            preferences = decode(body)
            return json(normalizedPreferences())
        case ("POST", "account/devices"):
            return json(["success": true])
        case ("GET", "account/payment-methods"):
            return json([
                ["id": "pm_demo1", "brand": "visa", "last4": "4242", "exp_month": 8, "exp_year": 2029, "default": true, "created_at": "2026-01-10T10:00:00Z"],
                ["id": "pm_demo2", "brand": "mastercard", "last4": "4444", "exp_month": 3, "exp_year": 2028, "default": false, "created_at": "2026-05-02T10:00:00Z"],
            ])
        case ("POST", "account/payment-methods/checkout"):
            return json(["url": "https://stripe.com"])
        case ("GET", "invoices"):
            return json(["items": invoices(), "total": 3])
        case ("GET", "notifications"):
            return json(["items": notifications(), "total": 4])
        case ("GET", "notifications/types"):
            return json(notificationTypes())
        case ("POST", "notifications/report-error"):
            return json(["success": true])
        case ("GET", "transactions"):
            return json(sessionsPage(status: parameter("status"), station: parameter("stationId"), offset: Int(parameter("offset") ?? "0") ?? 0, limit: Int(parameter("limit") ?? "30") ?? 30))
        case ("POST", "transactions/start"):
            let input = decode(body)
            guard live == nil else { return problem(409, "Une session est déjà en cours sur ce connecteur.") }
            nextSession += 1
            live = Live(id: nextSession, station: input["stationId"] as? String ?? "", connector: input["connectorId"] as? Int ?? 1, token: input["tokenUid"] as? String ?? "", startedAt: .now)
            return json(["id": UUID().uuidString.lowercased(), "state": "QUEUED"])
        case ("GET", "statistics/years"):
            return json([2026, 2025])
        default:
            break
        }

        if parts.count >= 5, parts[2] == "account", parts[3] == "stations" {
            let station = parts[4]
            if parts.count == 5, method == "GET" { return detail(station: station, signedIn: true) }
            if parts.count == 6, parts[5] == "photos" {
                if method == "GET" {
                    return json(photos.filter { $0.station == station }.reversed().map(photoJSON))
                }
                let photo = Photo(id: UUID().uuidString.lowercased(), station: station, data: body ?? Data(), mine: true, createdAt: .now)
                photos.append(photo)
                return json(photoJSON(photo))
            }
            if parts.count == 7, parts[5] == "photos" {
                if method == "DELETE" {
                    photos.removeAll { $0.id == parts[6] }
                    return json(["success": true])
                }
                if let photo = photos.first(where: { $0.id == parts[6] }) {
                    return (200, photo.data, "image/jpeg")
                }
            }
        }
        if parts.count >= 6, parts[2] == "public", parts[3] == "workspaces", parts[5] == "stations", parts.count == 7 {
            return detail(station: parts[6], signedIn: false)
        }
        if parts.count == 6, parts[2] == "account", parts[3] == "tokens", parts[5] == "wallet-pass" {
            return problem(503, "La démonstration ne peut pas signer de passe : Apple n’accepte que les passes signés par le certificat de l’exploitant.")
        }
        if parts.count == 4, parts[2] == "account", parts[3] == "cars" {
            return json(["success": true])
        }
        if parts.count >= 5, parts[2] == "account", parts[3] == "cars" {
            if parts.count == 5, method == "PUT" {
                let input = decode(body)
                if let index = cars.firstIndex(where: { $0["id"] as? String == parts[4] }) {
                    cars[index]["name"] = input["name"]
                    cars[index]["data"] = input["data"]
                }
                return json(["id": parts[4], "revision": 2])
            }
            if parts.count == 5, method == "DELETE" {
                cars.removeAll { $0["id"] as? String == parts[4] }
                return json(["success": true])
            }
            if parts.count == 6, parts[5] == "autocharge" {
                return json([])
            }
        }
        if parts.count == 5, parts[2] == "account", parts[3] == "devices" {
            return json(["success": true])
        }
        if parts.count >= 5, parts[2] == "account", parts[3] == "payment-methods" {
            return json(["success": true])
        }
        if parts.count == 5, parts[2] == "invoices" || parts[3] == "invoices" {
            return json(["url": "https://stripe.com"])
        }
        if parts.count >= 4, parts[2] == "transactions", let id = Int(parts[3]) {
            if parts.count == 4 { return session(id: id) }
            if parts[4] == "consumptions" { return json(consumptions(id: id)) }
            if parts[4] == "signed-data" {
                return json(["session_id": id, "station_id": "PAR-01", "format": "OCMF", "start": "OCMF|{\"FV\":\"1.0\"}|{\"SA\":\"ECDSA\"}", "stop": "OCMF|{\"FV\":\"1.0\"}|{\"SA\":\"ECDSA\"}"])
            }
            if parts[4] == "stop" {
                stopLive()
                return json(["id": UUID().uuidString.lowercased(), "state": "QUEUED"])
            }
        }
        if parts.count == 4, parts[2] == "statistics" {
            return json(statistics(metric: parts[3], year: Int(parameter("year") ?? "2026") ?? 2026))
        }
        if parts.count == 6, parts[2] == "public", parts[3] == "evses" {
            return json(portal())
        }
        if parts.count == 7, parts[2] == "public", parts[3] == "evses", parts[6] == "checkout" {
            let input = decode(body)
            let order = UUID().uuidString.lowercased()
            guests[order] = Guest(id: order, credit: input["creditMinor"] as? Int ?? 2000, createdAt: .now)
            return json(["orderId": order, "checkoutUrl": "https://stripe.com"])
        }
        if parts.count >= 5, parts[2] == "public", parts[3] == "sessions" {
            guard var guest = guests[parts[4]] else { return problem(404, "Session introuvable.") }
            if parts.count == 6, parts[5] == "stop" {
                guest.stoppedAt = .now
                guests[parts[4]] = guest
            }
            return json(guestJSON(guest))
        }
        return problem(404, "Route de démonstration inconnue : \(route)")
    }

    private func principal() -> [String: Any] {
        [
            "id": DemoData.userId, "tenantId": DemoData.tenantId, "email": DemoData.email, "name": "Thomas Bernard",
            "role": "DRIVER", "platformAdmin": false, "eulaAccepted": eulaAccepted,
        ]
    }

    private func cardOrder() -> [String: Any] {
        [
            "id": "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff", "state": cardActivated ? "ACTIVATED" : "SHIPPED", "amount_minor": 1500, "currency": "EUR",
            "created_at": date(.now.addingTimeInterval(-432_000)), "paid_at": date(.now.addingTimeInterval(-432_000)), "shipped_at": date(.now.addingTimeInterval(-172_800)),
            "activated_at": nullable(cardActivated ? date(.now) : nil), "refunded": false,
        ]
    }

    private func orderedCardToken() -> [String: Any] {
        [
            "uid": DemoData.orderedCard, "user_id": DemoData.userId, "type": "RFID", "enabled": true, "expires_at": NSNull(), "visual_id": DemoData.orderedCardNumber,
            "description": "Carte commandée", "user_name": "Thomas Bernard", "default": false, "last_used_at": NSNull(), "vehicle_id": NSNull(), "vehicle_name": NSNull(),
        ]
    }

    private func tokens() -> [[String: Any]] {
        (cardActivated ? [orderedCardToken()] : []) + [
            [
                "uid": DemoData.ownCard, "user_id": DemoData.userId, "type": "RFID", "enabled": true, "expires_at": NSNull(), "visual_id": "CARTE-1042",
                "description": "Carte principale", "user_name": "Thomas Bernard", "default": true, "last_used_at": date(.now.addingTimeInterval(-86_400)), "vehicle_id": NSNull(), "vehicle_name": NSNull(),
            ],
            [
                "uid": "04D5E6F7A8B990", "user_id": DemoData.userId, "type": "RFID", "enabled": false, "expires_at": NSNull(), "visual_id": "CARTE-0877",
                "description": "Ancienne carte", "user_name": "Thomas Bernard", "default": false, "last_used_at": "2025-11-02T09:00:00Z", "vehicle_id": NSNull(), "vehicle_name": NSNull(),
            ],
        ]
    }

    private func connector(_ id: Int, status: String, type: String, power: Double, stationId: String) -> [String: Any] {
        let isMine = live?.station == stationId && live?.connector == id
        return [
            "connector_id": id, "status": isMine ? "Charging" : status, "error_code": "NoError", "status_at": date(.now.addingTimeInterval(-1200)),
            "type": type, "maximum_power_kw": power, "phases": 3,
            "current_power_kw": nullable(isMine ? livePower() : nil), "current_transaction_id": nullable(isMine ? live?.id : nil),
            "current_energy_kwh": nullable(isMine ? liveEnergy() : nil), "current_soc": nullable(isMine ? liveSoc() : nil), "current_user_name": NSNull(), "inactive_since": NSNull(),
        ]
    }

    private func station(_ id: String, _ name: String, _ lat: Double, _ lon: Double, site: String, address: String, postal: String, city: String, connected: Bool, plugs: [[String: Any]], evse: Bool, photoCount: Any = NSNull(), tariff: Any = NSNull()) -> [String: Any] {
        [
            "id": id, "name": name, "vendor": "Schneider Electric", "model": "EVlink Pro", "connected": connected, "last_seen": date(.now),
            "connectors": plugs, "public": true, "latitude": lat, "longitude": lon, "maximum_power_kw": plugs.compactMap { $0["maximum_power_kw"] as? Double }.max() ?? 22,
            "auto_charge": id == "PAR-02", "site_id": "c0000000-0000-4000-8000-000000000002", "site_area_id": "d0000000-0000-4000-8000-000000000002",
            "site_name": site, "address": address, "postal_code": postal, "city": city, "country": "FR",
            "public_evses": evse ? [["id": DemoData.evseId, "connector_id": 1, "workspace_slug": "demo", "name": name]] : [],
            "photo_count": photoCount, "tariff": tariff,
        ]
    }

    private func stations(signedIn: Bool, photoCount: Any = NSNull(), tariff: Any = NSNull(), only: String? = nil) -> [[String: Any]] {
        func make(_ id: String, _ name: String, _ lat: Double, _ lon: Double, _ site: String, _ address: String, _ postal: String, _ connected: Bool, _ plugs: [(Int, String, String, Double)], evse: Bool = false) -> [String: Any]? {
            guard only == nil || only == id else { return nil }
            return station(id, name, lat, lon, site: site, address: address, postal: postal, city: "Paris", connected: connected, plugs: plugs.map { connector($0.0, status: $0.1, type: $0.2, power: $0.3, stationId: id) }, evse: evse, photoCount: photoCount, tariff: tariff)
        }
        return [
            make("PAR-01", "Gare de Lyon — Borne A", 48.8443, 2.3744, "Gare de Lyon", "20 boulevard Diderot", "75012", true, [(1, "Available", "T2", 22), (2, "Available", "T2", 22)], evse: true),
            make("PAR-02", "Bercy Village — Rapide", 48.8326, 2.3862, "Bercy Village", "28 rue François Truffaut", "75012", true, [(1, "Charging", "CCS", 150), (2, "Available", "CCS", 150)]),
            make("PAR-03", "Bastille — Parking", 48.8530, 2.3690, "Parking Bastille", "2 rue de la Roquette", "75011", true, [(1, "Charging", "T2", 11), (2, "SuspendedEV", "T2", 11)]),
            make("PAR-04", "République — Voirie", 48.8675, 2.3637, "Place de la République", "12 place de la République", "75003", true, [(1, "Faulted", "T2", 22)]),
            make("PAR-05", "Hôtel de Ville", 48.8566, 2.3522, "Hôtel de Ville", "5 rue de Lobau", "75004", false, [(1, "Unavailable", "T2", 22)]),
            make("PAR-06", "Nation — Rapide", 48.8484, 2.3958, "Place de la Nation", "1 avenue du Trône", "75011", true, [(1, "Available", "CCS", 50), (2, "Preparing", "CHADEMO", 50)]),
            make("PAR-07", "Montparnasse", 48.8422, 2.3219, "Gare Montparnasse", "17 boulevard de Vaugirard", "75015", true, [(1, "Available", "T2", 22)]),
        ].compactMap { $0 }
    }

    private func detail(station id: String, signedIn: Bool) -> (Int, Data, String) {
        let tariff: [String: Any] = ["name": "Tarif réseau", "currency": "EUR", "flat_price": 0.5, "energy_price": 0.39, "time_price": 0.6, "parking_price": 6]
        guard let value = stations(signedIn: signedIn, photoCount: photos.filter { $0.station == id }.count, tariff: tariff, only: id).first else {
            return problem(404, "Borne introuvable.")
        }
        return json(value)
    }

    private func photoJSON(_ photo: Photo) -> [String: Any] {
        [
            "id": photo.id, "station_id": photo.station, "content_type": "image/jpeg", "created_at": date(photo.createdAt),
            "mine": photo.mine, "path": "/api/v1/account/stations/\(photo.station)/photos/\(photo.id)",
        ]
    }

    private func elapsed() -> Double { live.map { Date.now.timeIntervalSince($0.startedAt) * 60 } ?? 0 }

    private func livePower() -> Double { (10.4 + sin(elapsed() / 300) * 0.6).rounded(toPlaces: 2) }

    private func liveEnergy() -> Double { (elapsed() / 3600 * 10.8 + 3.2).rounded(toPlaces: 2) }

    private func liveSoc() -> Int { min(100, 42 + Int(liveEnergy() / 60 * 100)) }

    private func stopLive() {
        guard let live else { return }
        finished.insert(sessionJSON(live, stoppedAt: .now), at: 0)
        self.live = nil
    }

    private func sessionJSON(_ live: Live, stoppedAt: Date? = nil) -> [String: Any] {
        let energy = liveEnergy()
        return [
            "id": live.id, "station_id": live.station, "connector_id": live.connector, "started_at": date(live.startedAt.addingTimeInterval(-1140)),
            "stopped_at": nullable(stoppedAt.map(date)), "energy_kwh": energy, "authorization_status": "Accepted", "stop_reason": nullable(stoppedAt == nil ? nil : "Remote"),
            "token_uid": live.token, "user_id": DemoData.userId, "user_name": "Thomas Bernard", "site_area_id": NSNull(), "site_id": NSNull(),
            "duration_seconds": Int(Date.now.timeIntervalSince(live.startedAt)) + 1140, "inactivity_seconds": 0, "current_power_kw": nullable(stoppedAt == nil ? livePower() : nil),
            "state_of_charge_start": 42, "state_of_charge": liveSoc(), "price": (energy * 0.39 + 0.5).rounded(toPlaces: 2), "currency": "EUR", "pricing_definition_id": NSNull(),
            "billing_status": stoppedAt == nil ? "NONE" : "PENDING", "invoice_id": NSNull(), "roaming_partner": NSNull(), "public_payment": false, "soft_stopped": false,
            "vehicle_id": nullable(defaultCar), "auto_charge": false, "vid": NSNull(), "errors": NSNull(), "signed_meter_data": false, "signed_meter_format": NSNull(),
        ]
    }

    private func makeHistory(index: Int) -> [String: Any] {
        let start = Date.now.addingTimeInterval(-Double(index + 1) * 201_600)
        let stations = ["PAR-01", "PAR-02", "PAR-06", "PAR-07"]
        let energy = [28.4, 41.2, 18.6, 33.9, 12.3, 47.5, 22.8, 36.1, 15.2][index]
        let seconds = Int(energy * 330)
        return [
            "id": 2140 - index, "station_id": stations[index % 4], "connector_id": 1, "started_at": date(start), "stopped_at": date(start.addingTimeInterval(Double(seconds))),
            "energy_kwh": energy, "authorization_status": "Accepted", "stop_reason": "EVDisconnected", "token_uid": DemoData.ownCard, "user_id": DemoData.userId, "user_name": "Thomas Bernard",
            "site_area_id": NSNull(), "site_id": NSNull(), "duration_seconds": seconds, "inactivity_seconds": index * 120, "current_power_kw": NSNull(),
            "state_of_charge_start": 24 + index, "state_of_charge": min(100, 24 + index + Int(energy / 60 * 100)), "price": (energy * 0.39 + 0.5).rounded(toPlaces: 2), "currency": "EUR",
            "pricing_definition_id": NSNull(), "billing_status": index < 2 ? "INVOICED" : "PAID", "invoice_id": NSNull(), "roaming_partner": NSNull(), "public_payment": false,
            "soft_stopped": false, "vehicle_id": nullable(defaultCar), "auto_charge": false, "vid": NSNull(), "errors": NSNull(), "signed_meter_data": index == 0, "signed_meter_format": nullable(index == 0 ? "OCMF" : nil),
        ]
    }

    private func sessionsPage(status: String?, station: String?, offset: Int, limit: Int) -> [String: Any] {
        var rows: [[String: Any]] = []
        if let live, status != "completed", station == nil || station == live.station {
            rows.append(sessionJSON(live))
        }
        if status != "active" {
            rows += finished.filter { station == nil || $0["station_id"] as? String == station }
        }
        return ["items": Array(rows.dropFirst(offset).prefix(limit)), "total": rows.count]
    }

    private func session(id: Int) -> (Int, Data, String) {
        if let live, live.id == id { return json(sessionJSON(live)) }
        if var row = finished.first(where: { $0["id"] as? Int == id }) {
            row["errors"] = []
            return json(row)
        }
        return problem(404, "Session introuvable.")
    }

    private func consumptions(id: Int) -> [[String: Any]] {
        let isLive = live?.id == id
        let samples = 30
        let step = isLive ? max(60, Date.now.timeIntervalSince(live!.startedAt.addingTimeInterval(-1140)) / Double(samples)) : 300
        let start = isLive ? Date.now.addingTimeInterval(-step * Double(samples)) : Date.now.addingTimeInterval(-90_000)
        return (0..<samples).map { index in
            let progress = Double(index) / Double(samples)
            let power = min(10.8, Double(index) * 2.8) * (progress > 0.82 && !isLive ? 0.55 : 1) + sin(Double(index)) * 0.3
            return [
                "at": date(start.addingTimeInterval(step * Double(index))), "energy_kwh": (Double(index) * 0.9).rounded(toPlaces: 2),
                "power_kw": max(0, power).rounded(toPlaces: 2), "soc": 42 + index / 2, "amperage_a": 16, "voltage_v": 230, "cumulated_price": (Double(index) * 0.35).rounded(toPlaces: 2),
            ] as [String: Any]
        }
    }

    private func statistics(metric: String, year: Int) -> [String: Any] {
        let base = [62.0, 48, 71, 55, 83, 90, 76, 41, 95, 88, 64, 0]
        let scale: Double
        let unit: String
        switch metric {
        case "usage": (scale, unit) = (0.11, "h")
        case "inactivity": (scale, unit) = (0.02, "h")
        case "sessions": (scale, unit) = (0.08, "session")
        case "pricing": (scale, unit) = (0.39, "EUR")
        default: (scale, unit) = (1, "kWh")
        }
        let months = base.map { metric == "sessions" ? ($0 * scale).rounded() : ($0 * scale).rounded(toPlaces: 2) }
        let total = months.reduce(0, +).rounded(toPlaces: 2)
        return [
            "metric": metric, "year": year, "unit": unit, "currency": nullable(metric == "pricing" ? "EUR" : nil), "months": months, "total": total,
            "groups": [
                ["key": "PAR-01", "label": "Gare de Lyon — Borne A", "months": months.map { ($0 * 0.6).rounded(toPlaces: 2) }, "total": (total * 0.6).rounded(toPlaces: 2)],
                ["key": "PAR-02", "label": "Bercy Village — Rapide", "months": months.map { ($0 * 0.4).rounded(toPlaces: 2) }, "total": (total * 0.4).rounded(toPlaces: 2)],
            ],
        ]
    }

    private func catalog(search: String) -> [String: Any] {
        let rows: [(String, String, String, Double, Double, Double)] = [
            ("Renault", "Megane E-Tech", "EV60", 60, 22, 130), ("Peugeot", "e-208", "GT", 51, 11, 100), ("Tesla", "Model 3", "Long Range", 75, 11, 250),
            ("Volkswagen", "ID.3", "Pro S", 77, 11, 170), ("Hyundai", "Kona Electric", "65 kWh", 65, 11, 102), ("Citroën", "ë-C4", "54 kWh", 54, 11, 100),
        ]
        let items: [[String: Any]] = rows
            .filter { search.isEmpty || "\($0.0) \($0.1)".localizedCaseInsensitiveContains(search) }
            .map { row in
                [
                    "id": UUID().uuidString.lowercased(), "name": "\(row.0) \(row.1)", "revision": 0, "updated_at": "2026-01-01T00:00:00Z",
                    "data": ["make": row.0, "model": row.1, "version": row.2, "batteryKwh": row.3, "maxAcPowerKw": row.4, "maxDcPowerKw": row.5] as [String: Any],
                ]
            }
        return ["items": items, "total": items.count]
    }

    private func invoices() -> [[String: Any]] {
        [
            ["id": UUID().uuidString.lowercased(), "number": "FA-2026-0183", "user_id": DemoData.userId, "user_name": "Thomas Bernard", "status": "OPEN", "amount_minor": 3214, "currency": "EUR", "created_at": "2026-11-01T06:00:00Z", "session_ids": [2140, 2139], "hosted_url": "https://stripe.com", "last_error": NSNull()],
            ["id": UUID().uuidString.lowercased(), "number": "FA-2026-0147", "user_id": DemoData.userId, "user_name": "Thomas Bernard", "status": "PAID", "amount_minor": 5890, "currency": "EUR", "created_at": "2026-10-01T06:00:00Z", "session_ids": [2120], "hosted_url": "https://stripe.com", "last_error": NSNull()],
            ["id": UUID().uuidString.lowercased(), "number": "FA-2026-0112", "user_id": DemoData.userId, "user_name": "Thomas Bernard", "status": "PAID", "amount_minor": 4125, "currency": "EUR", "created_at": "2026-09-01T06:00:00Z", "session_ids": [2101], "hosted_url": "https://stripe.com", "last_error": NSNull()],
        ]
    }

    private func notifications() -> [[String: Any]] {
        let rows: [(String, String, Double)] = [
            ("END_OF_SESSION", "Votre session de recharge est terminée", -86_400),
            ("END_OF_CHARGE", "La charge de votre véhicule est terminée", -90_000),
            ("SESSION_STARTED", "Votre session de recharge a commencé", -100_000),
            ("INVOICE_AVAILABLE", "Votre facture est disponible", -259_200),
        ]
        return rows.map { row in
            [
                "id": UUID().uuidString.lowercased(), "type": row.0, "user_id": DemoData.userId, "recipient": DemoData.email, "subject": row.1,
                "channel": "EMAIL", "created_at": date(.now.addingTimeInterval(row.2)), "sent_at": date(.now.addingTimeInterval(row.2)),
            ]
        }
    }

    private func notificationTypes() -> [[String: Any]] {
        [
            ("SESSION_STARTED", "Début de session"), ("END_OF_CHARGE", "Fin de charge"), ("END_OF_SESSION", "Fin de session"),
            ("ACCOUNT_STATUS_CHANGED", "Changement d’état du compte"), ("ACCOUNT_INACTIVITY", "Compte inactif"),
            ("PREPARING_SESSION_NOT_STARTED", "Véhicule branché sans session"), ("SESSION_NOT_STARTED_AFTER_AUTHORIZE", "Badge présenté sans session"),
            ("INVOICE_AVAILABLE", "Facture disponible"), ("BILLING_FAILED", "Échec de paiement"),
        ].map { ["type": $0.0, "label": $0.1, "audience": "DRIVER"] }
    }

    private func normalizedPreferences() -> [String: Any] {
        var types: [String: Bool] = [:]
        notificationTypes().forEach { types[$0["type"] as? String ?? ""] = true }
        (preferences["types"] as? [String: Bool])?.forEach { types[$0.key] = $0.value }
        return ["email": preferences["email"] ?? true, "push": preferences["push"] ?? true, "types": types]
    }

    private func portal() -> [String: Any] {
        [
            "id": DemoData.evseId, "workspace": "Réseau Démo", "workspaceSlug": "demo", "name": "Gare de Lyon — Borne A", "stationId": "PAR-01", "connectorId": 1,
            "connected": true, "status": live?.station == "PAR-01" ? "Charging" : "Available", "available": live?.station != "PAR-01",
            "tariff": ["currency": "EUR", "energyPrice": 0.39, "timePrice": 0.6, "flatPrice": 0.5], "minimumCreditMinor": 500, "maximumCreditMinor": 10000,
        ]
    }

    private func guestJSON(_ guest: Guest) -> [String: Any] {
        let age = Date.now.timeIntervalSince(guest.createdAt)
        let state: String
        var billed: Any = NSNull()
        var refunded = 0
        if guest.stoppedAt != nil {
            state = "COMPLETED"
            billed = min(guest.credit, 742)
            refunded = guest.credit - min(guest.credit, 742)
        } else if age < 4 {
            state = "START_REQUESTED"
        } else {
            state = "CHARGING"
        }
        return [
            "id": guest.id, "state": state, "prepaidMinor": guest.credit, "billedMinor": billed, "refundedMinor": refunded, "currency": "EUR",
            "transactionId": nullable(state == "START_REQUESTED" ? nil : 2200), "message": NSNull(),
        ]
    }
}

private extension Double {
    func rounded(toPlaces places: Int) -> Double {
        let factor = pow(10, Double(places))
        return (self * factor).rounded() / factor
    }
}
#endif
