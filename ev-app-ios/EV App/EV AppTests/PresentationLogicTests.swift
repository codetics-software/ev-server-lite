//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import SwiftUI
import Testing
@testable import EV_App

@MainActor
struct ConnectorStatusTests {
    @Test func mapsOCPPStatuses() {
        let expected: [(String, ConnectorStatus)] = [
            ("Available", .available), ("Preparing", .pluggedIn), ("Charging", .charging), ("SuspendedEV", .paused),
            ("SuspendedEVSE", .paused), ("Finishing", .finishing), ("Reserved", .reserved), ("Unavailable", .unavailable),
            ("Faulted", .faulted), ("Occupied", .unknown), ("available", .unknown),
        ]
        for (value, status) in expected {
            #expect(ConnectorStatus(value) == status)
        }
    }

    @Test func onlyFreeConnectorsCanStart() {
        let startable = [ConnectorStatus.available, .pluggedIn, .charging, .paused, .finishing, .reserved, .unavailable, .faulted, .unknown]
            .filter(\.isStartable)
        #expect(startable == [.available, .pluggedIn])
    }

    @Test func colorsFollowTheStatusFamily() {
        #expect(ConnectorStatus.available.color == .green)
        #expect(ConnectorStatus.charging.color == .blue)
        #expect(ConnectorStatus.pluggedIn.color == .blue)
        #expect(ConnectorStatus.paused.color == .blue)
        #expect(ConnectorStatus.finishing.color == .blue)
        #expect(ConnectorStatus.reserved.color == .orange)
        #expect(ConnectorStatus.faulted.color == .red)
        #expect(ConnectorStatus.unavailable.color == .gray)
        #expect(ConnectorStatus.unknown.color == .gray)
    }

    @Test func labelsUseDriverWording() {
        #expect(ConnectorStatus.pluggedIn.label.key == "Plugged in")
        #expect(ConnectorStatus.faulted.label.key == "Out of order")
        #expect(ConnectorStatus.paused.label.key == "Paused")
        #expect(ConnectorStatus.available.label.key == "Available")
        #expect(ConnectorStatus.charging.label.key == "Charging")
        #expect(ConnectorStatus.finishing.label.key == "Finishing")
        #expect(ConnectorStatus.reserved.label.key == "Reserved")
        #expect(ConnectorStatus.unavailable.label.key == "Unavailable")
        #expect(ConnectorStatus.unknown.label.key == "Unknown")
    }
}

@MainActor
struct ConnectorTypeTests {
    @Test func normalisesKnownPlugNames() {
        for value in ["T2", "Type2", "TYPE2", "IEC_62196_T2"] { #expect(ConnectorType.label(for: value) == "Type 2") }
        for value in ["CCS", "CCS2", "IEC_62196_T2_COMBO"] { #expect(ConnectorType.label(for: value) == "CCS") }
        for value in ["CHADEMO", "CHAdeMO"] { #expect(ConnectorType.label(for: value) == "CHAdeMO") }
        for value in ["T3", "IEC_62196_T3"] { #expect(ConnectorType.label(for: value) == "Type 3") }
        for value in ["DOMESTIC", "Domestic", "E/F"] { #expect(ConnectorType.label(for: value) == String(localized: "Domestic")) }
    }

    @Test func keepsUnknownNamesAndLabelsMissingOnes() {
        #expect(ConnectorType.label(for: "Tesla") == "Tesla")
        #expect(ConnectorType.label(for: nil) == String(localized: "Connector"))
    }
}

@MainActor
struct StationAvailabilityTests {
    @Test func offlineStationsAreNeverAvailable() {
        let station = TestData.station(connected: false, connectors: [TestData.connector(status: "Available")])
        let availability = StationAvailability(station)
        #expect(!availability.isAvailable)
        #expect(availability.label.key == "Offline")
        #expect(availability.color == .gray)
    }

    @Test func countsFreeAndPluggedInConnectors() {
        let station = TestData.station(connectors: [
            TestData.connector(1, status: "Available"),
            TestData.connector(2, status: "Preparing"),
            TestData.connector(3, status: "Charging"),
        ])
        let availability = StationAvailability(station)
        guard case .available(let count) = availability else {
            Issue.record("expected available, got \(availability)")
            return
        }
        #expect(count == 2)
        #expect(availability.isAvailable)
        #expect(availability.color == .green)
        #expect(availability.label.key == "%lld available")
    }

    @Test func reportsOutOfOrderOnlyWhenEveryConnectorIsBroken() {
        let broken = TestData.station(connectors: [TestData.connector(1, status: "Faulted"), TestData.connector(2, status: "Unavailable")])
        let outOfOrder = StationAvailability(broken)
        #expect(outOfOrder.label.key == "Out of order")
        #expect(outOfOrder.color == .red)
        let mixed = TestData.station(connectors: [TestData.connector(1, status: "Faulted"), TestData.connector(2, status: "Charging")])
        #expect(StationAvailability(mixed).label.key == "In use")
        #expect(StationAvailability(mixed).color == .blue)
    }

    @Test func stationWithoutConnectorsIsBusy() {
        let availability = StationAvailability(TestData.station(connectors: []))
        #expect(availability.label.key == "In use")
        #expect(!availability.isAvailable)
    }
}

@MainActor
struct StatusLabelTests {
    @Test func stopReasonsAreTranslatedForDrivers() {
        #expect(StopReason.label(for: "EVDisconnected") == String(localized: "Vehicle unplugged"))
        #expect(StopReason.label(for: "Remote") == String(localized: "Stopped remotely"))
        #expect(StopReason.label(for: "Local") == String(localized: "Stopped at the station"))
        #expect(StopReason.label(for: "SOCLimitReached") == String(localized: "Battery limit reached"))
        #expect(StopReason.label(for: "StaleSession") == String(localized: "Closed without news from the station"))
        #expect(StopReason.label(for: "SoftStop") == String(localized: "Closed by the operator"))
        #expect(StopReason.label(for: "LocalOutOfCredit") == String(localized: "Credit exhausted"))
        for reset in ["HardReset", "SoftReset", "Reboot", "ImmediateReset"] {
            #expect(StopReason.label(for: reset) == String(localized: "Station restarted"))
        }
    }

    @Test func everyKnownStopReasonHasItsOwnLabel() {
        let reasons = [
            "Local", "Remote", "EVDisconnected", "EmergencyStop", "PowerLoss", "HardReset", "UnlockCommand", "DeAuthorized", "StoppedByEV",
            "SOCLimitReached", "TimeLimitReached", "EnergyLimitReached", "Timeout", "MasterPass", "LocalOutOfCredit", "GroundFault",
            "OvercurrentFault", "PowerQuality", "StaleSession", "SoftStop", "Other",
        ]
        let labels = reasons.map(StopReason.label(for:))
        #expect(Set(labels).count == reasons.count)
        #expect(zip(reasons, labels).allSatisfy { $0 != $1 })
    }

    @Test func unknownStopReasonIsShownAsIs() {
        #expect(StopReason.label(for: "VendorSpecific42") == "VendorSpecific42")
    }

    @Test func billingStatuses() {
        #expect(BillingStatus.label("NONE").key == "Not billed")
        #expect(BillingStatus.label("PENDING").key == "Pending")
        #expect(BillingStatus.label("INVOICED").key == "Invoiced")
        #expect(BillingStatus.label("PAID").key == "Paid")
        #expect(BillingStatus.label("FAILED").key == "Payment failed")
        #expect(BillingStatus.label("SOMETHING").key == "Unknown")
    }

    @Test func invoiceStatuses() {
        #expect(InvoiceStatus.label("DRAFT").key == "Draft")
        #expect(InvoiceStatus.label("OPEN").key == "To pay")
        #expect(InvoiceStatus.label("PAID").key == "Paid")
        #expect(InvoiceStatus.label("FAILED").key == "Failed")
        #expect(InvoiceStatus.label("VOID").key == "Cancelled")
        #expect(InvoiceStatus.label("UNCOLLECTIBLE").key == "Unpaid")
        #expect(InvoiceStatus.label("?").key == "Unknown")
    }

    @Test func paymentStatesSeparateFinishedFromOngoing() {
        let finished: Set<PaymentState> = [.completed, .cancelled, .failed, .disputed]
        let all: [PaymentState] = [.pendingPayment, .paid, .startRequested, .charging, .stopRequested, .settling, .refundPending, .completed, .cancelled, .failed, .disputed]
        for state in all {
            #expect(state.isFinished == finished.contains(state))
        }
        #expect(Set(all.map(\.label.key)).count == all.count)
        #expect(PaymentState.failed.label.key == "Needs attention")
        #expect(PaymentState.settling.label.key == "Calculating the balance")
    }

    @Test func paymentStatesExplainOnlyWhatNeedsExplaining() {
        let explained: [PaymentState] = [.pendingPayment, .startRequested, .stopRequested, .failed]
        for state in explained { #expect(state.explanation != nil) }
        for state in [PaymentState.paid, .charging, .settling, .refundPending, .completed, .cancelled, .disputed] {
            #expect(state.explanation == nil)
        }
    }

    @Test func paymentStateDecodesServerValues() throws {
        let states = try JSONCoding.decoder.decode([PaymentState].self, from: Data(#"["PENDING_PAYMENT","REFUND_PENDING","DISPUTED"]"#.utf8))
        #expect(states == [.pendingPayment, .refundPending, .disputed])
    }

    @Test func cardOrderStates() {
        #expect(CardOrderState.pendingPayment.label.key == "Waiting for payment")
        #expect(CardOrderState.paid.label.key == "Paid, being prepared")
        #expect(CardOrderState.shipped.label.key == "Ready to activate")
        #expect(CardOrderState.activated.label.key == "Activated")
        #expect(CardOrderState.cancelled.label.key == "Cancelled")
        #expect(CardOrderState.paid.color == .orange)
        #expect(CardOrderState.shipped.color == .blue)
        #expect(CardOrderState.activated.color == .green)
        #expect(CardOrderState.cancelled.color == .secondary)
        #expect(CardOrderState.pendingPayment.color == .secondary)
    }
}

@MainActor
struct StatisticsMetricTests {
    @Test func exposesEveryServerMetric() {
        #expect(StatisticsMetric.allCases.map(\.id) == ["consumption", "usage", "inactivity", "sessions", "pricing"])
        #expect(StatisticsMetric.allCases.map(\.label.key) == ["Energy", "Charging time", "Idle time", "Sessions", "Spending"])
    }

    @Test func formatsHoursAsDurations() {
        #expect(StatisticsMetric.usage.format(1.5, currency: nil) == Formatters.duration(seconds: 5_400))
        #expect(StatisticsMetric.inactivity.format(0.25, currency: nil) == Formatters.duration(seconds: 900))
    }

    @Test func formatsOtherMetrics() {
        #expect(StatisticsMetric.consumption.format(42.5, currency: nil) == Formatters.energy(42.5))
        #expect(StatisticsMetric.sessions.format(7, currency: nil) == "7")
        #expect(StatisticsMetric.sessions.format(6.6, currency: nil) == "7")
        let euros = StatisticsMetric.pricing.format(12.5, currency: nil)
        #expect(euros == StatisticsMetric.pricing.format(12.5, currency: "EUR"))
        #expect(euros != StatisticsMetric.pricing.format(12.5, currency: "USD"))
        #expect(euros.filter(\.isNumber) == "1250")
    }
}

@MainActor
struct SmallValueTypeTests {
    @Test func startRequestIdentifiesTheConnector() {
        let request = StartRequest(stationId: "PAR-01", stationName: "Gare", connectorId: 2)
        #expect(request.id == "PAR-01/2")
        #expect(request != StartRequest(stationId: "PAR-01", stationName: "Gare", connectorId: 1))
    }

    @Test func vehicleEditorTargetIdentity() {
        let car = RecordResponse(id: UUID(), name: "Zoé", data: CarData(), revision: 1, updatedAt: .now)
        #expect(VehicleEditorTarget.new.id == "new")
        #expect(VehicleEditorTarget.edit(car).id == car.id.uuidString)
    }

    @Test func identifiersOfWrappers() {
        let url = URL(string: "https://codetics.fr")!
        #expect(IdentifiableURL(url: url).id == url)
        let evse = UUID()
        #expect(PortalLink(workspace: "demo", evseId: evse).id == evse)
    }

    @Test func emptyStringsBecomeNil() {
        #expect("".nilIfEmpty == nil)
        #expect(" ".nilIfEmpty == " ")
        #expect("Zoé".nilIfEmpty == "Zoé")
    }

    @Test func guestAccessTokensAreLongRandomHex() {
        let first = GuestAccessToken.generate()
        let second = GuestAccessToken.generate()
        #expect(first.count == 64)
        #expect(first.allSatisfy { $0.isHexDigit && !$0.isUppercase })
        #expect(first != second)
    }

    @Test func chargeStartErrorTellsTheDriverWhatToCheck() {
        let message = ChargeStartError.notStarted.errorDescription
        #expect(message == String(localized: "The station has not confirmed the start. Check that your vehicle is plugged in and that the station accepted your badge, then try again."))
    }
}

struct APIErrorTests {
    @Test func serverMessageWinsOverGenericText() {
        #expect(APIError.server(status: 409, message: "Déjà en cours.").errorDescription == "Déjà en cours.")
        #expect(APIError.server(status: 500, message: nil).errorDescription == String(localized: "The server refused this request."))
    }

    @Test func otherCasesHaveTheirOwnMessage() {
        let messages = [APIError.unauthorized, .offline, .invalidResponse].map(\.errorDescription)
        #expect(Set(messages).count == 3)
        #expect(APIError.unauthorized.errorDescription == String(localized: "Your session has expired. Sign in again."))
    }

    @Test func exposesStatusAndCodeOfServerErrors() {
        let error = APIError.server(status: 403, message: "x", code: "email_not_verified")
        #expect(error.status == 403)
        #expect(error.code == "email_not_verified")
        #expect(APIError.offline.status == nil)
        #expect(APIError.unauthorized.code == nil)
    }
}

struct ConfigurationIssueTests {
    @Test func identifiersAreStablePerKey() {
        #expect(ConfigurationIssue.missingFile.id == "missingFile")
        #expect(ConfigurationIssue.placeholder(key: "API_URL").id == "placeholder.API_URL")
        #expect(ConfigurationIssue.invalidValue(key: "API_URL").id == "invalid.API_URL")
        #expect(ConfigurationIssue.insecureURL(key: "API_URL").id == "insecure.API_URL")
    }

    @Test func messagesNameTheFaultyKey() {
        for issue in [ConfigurationIssue.placeholder(key: "BRAND_NAME"), .invalidValue(key: "BRAND_NAME"), .insecureURL(key: "BRAND_NAME")] {
            #expect(issue.message.contains("BRAND_NAME"))
        }
        #expect(ConfigurationIssue.missingFile.message.contains("AppConfiguration.plist"))
    }
}
