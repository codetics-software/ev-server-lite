//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI
import Testing
@testable import EV_App

extension DemoBackedTests {
    @MainActor
    struct AccountViewTests {
        @Test func accountListsSectionsAndSignsOut() async throws {
            let model = await DemoEnvironment.signedIn()
            let host = ViewHost(AccountScreen())
            defer { host.close() }
            #expect(await host.waitFor("Thomas Bernard"))
            #expect(host.contains(DemoData.email))
            #expect(host.contains("My vehicles"))
            #expect(host.contains("Payment methods"))
            #expect(host.contains("Invoices"))
            #expect(host.contains("Delete my account"))
            #expect(host.activate(label: "Sign out"))
            #expect(await host.waitUntil { !model.isSignedIn })
        }

        @Test func profileLoadsAndRequestsANewPassword() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(NavigationStack { ProfileScreen() })
            defer { host.close() }
            #expect(await host.waitFor(DemoData.email))
            #expect(host.textField("Full name")?.text == "Thomas Bernard")
            #expect(host.textField("Phone")?.text == "+33 6 12 34 56 78")
            #expect(host.activate(label: "Change my password"))
            #expect(await host.waitFor("A link to choose a new password has been sent"))
        }

        @Test func sitesNotificationsAndAbout() async throws {
            await DemoEnvironment.signedIn()
            let sites = ViewHost(NavigationStack { MySitesScreen() })
            #expect(await sites.waitFor("Parking privé Codetics"))
            sites.close()
            let notifications = ViewHost(NavigationStack { NotificationsScreen() })
            #expect(await notifications.waitFor("Votre facture est disponible"))
            #expect(notifications.contains("Preferences"))
            notifications.close()
            let about = ViewHost(NavigationStack { AboutScreen() })
            defer { about.close() }
            #expect(await about.waitFor("EV Supervision Lite — built by Kevin Vavelin, Codetics"))
            #expect(about.contains("https://codetics.fr"))
            #expect(about.contains("support@codetics.fr"))
            #expect(about.contains("Réseau Démo"))
        }

        @Test func notificationPreferencesListDriverTopics() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(NavigationStack { NotificationPreferencesScreen() })
            defer { host.close() }
            #expect(await host.waitFor("Fin de charge"))
            #expect(host.contains("Push notifications"))
            #expect(host.contains("Échec de paiement"))
        }

        @Test func paymentMethodsAndInvoices() async throws {
            await DemoEnvironment.signedIn()
            let methods = ViewHost(NavigationStack { PaymentMethodsScreen() })
            #expect(await methods.waitFor("Visa •••• 4242"))
            #expect(methods.contains("Mastercard •••• 4444"))
            #expect(methods.contains("Default"))
            #expect(methods.contains("Add a payment method"))
            #expect(methods.activate(label: "Set as default"))
            await methods.settle()
            #expect(methods.contains("Visa •••• 4242"))
            methods.close()
            let invoices = ViewHost(NavigationStack { InvoicesScreen() })
            defer { invoices.close() }
            #expect(await invoices.waitFor("FA-2026-0183"))
            #expect(invoices.contains("To pay"))
            #expect(invoices.contains("FA-2026-0112"))
        }

        @Test func vehiclesAreListedAndDetailed() async throws {
            let model = await DemoEnvironment.signedIn()
            let list = ViewHost(NavigationStack { VehiclesScreen() })
            #expect(await list.waitFor("Renault Megane E-Tech"))
            #expect(list.contains("Add a vehicle"))
            list.close()
            let car = try #require(try await model.api?.cars().first { $0.name == "Renault Megane E-Tech" })
            let detail = ViewHost(NavigationStack { VehicleDetailScreen(car: car, candidates: []) {} })
            defer { detail.close() }
            #expect(await detail.waitFor("GH-482-TZ"))
            #expect(detail.contains("Automatic charging"))
            #expect(detail.contains("Plug this vehicle into a compatible station"))
        }

        @Test func editorPrefillsAnExistingVehicle() async throws {
            let model = await DemoEnvironment.signedIn()
            let car = try #require(try await model.api?.cars().first { $0.name == "Renault Megane E-Tech" })
            let host = ViewHost(VehicleEditorScreen(target: .edit(car)) {})
            defer { host.close() }
            #expect(await host.waitFor("Edit vehicle"))
            #expect(host.textField("Make")?.text == "Renault")
            #expect(host.textField("Licence plate")?.text == "GH-482-TZ")
            #expect(host.textField("Battery (kWh)")?.text == "60.0")
        }

        @Test func editorCreatesANewVehicle() async throws {
            let model = await DemoEnvironment.signedIn()
            let api = try #require(model.api)
            let saved = Box<Bool>()
            let host = ViewHost(VehicleEditorScreen(target: .new) { saved.value = true })
            defer { host.close() }
            #expect(await host.waitFor("New vehicle"))
            #expect(host.type("Kia", into: "Make"))
            #expect(host.type("EV6", into: "Model"))
            #expect(host.type("ab-987-cd", into: "Licence plate"))
            #expect(host.type("77,4", into: "Battery (kWh)"))
            await host.settle()
            #expect(host.activate(label: "Save"))
            #expect(await host.waitUntil { saved.value == true })
            let created = try #require(try await api.cars().first { $0.name == "Kia EV6" })
            #expect(created.data.licensePlate == "AB-987-CD")
            #expect(created.data.batteryKwh == 77.4)
            try await api.deleteCar(id: created.id)
        }

        @Test func catalogSearchesAndSelects() async throws {
            await DemoEnvironment.signedIn()
            let selected = Box<RecordResponse<CarCatalogData>>()
            let host = ViewHost(CarCatalogScreen { selected.value = $0 })
            defer { host.close() }
            #expect(await host.waitFor("Tesla Model 3"))
            #expect(host.contains("Peugeot e-208"))
            #expect(host.activate(labelPrefix: "Tesla Model 3"))
            #expect(await host.waitUntil { selected.value != nil })
            #expect(selected.value?.data.batteryKwh == 75)
        }
    }
}
