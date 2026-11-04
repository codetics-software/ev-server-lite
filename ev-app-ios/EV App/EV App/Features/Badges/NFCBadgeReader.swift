//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreNFC
import Foundation

nonisolated final class NFCBadgeReader: NSObject, NFCTagReaderSessionDelegate, @unchecked Sendable {
    enum Failure: LocalizedError {
        case unavailable
        case unsupportedCard

        var errorDescription: String? {
            switch self {
            case .unavailable: String(localized: "This device cannot read NFC cards.")
            case .unsupportedCard: String(localized: "This type of card is not supported.")
            }
        }
    }

    static var isAvailable: Bool { NFCTagReaderSession.readingAvailable }

    private var session: NFCTagReaderSession?
    private var continuation: CheckedContinuation<String?, any Error>?
    private let alert: String
    private let success: String

    init(alert: String, success: String) {
        self.alert = alert
        self.success = success
    }

    func scan() async throws -> String? {
        guard Self.isAvailable, let session = NFCTagReaderSession(pollingOption: [.iso14443, .iso15693], delegate: self) else {
            throw Failure.unavailable
        }
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            self.session = session
            session.alertMessage = alert
            session.begin()
        }
    }

    private func finish(_ result: Result<String?, any Error>) {
        continuation?.resume(with: result)
        continuation = nil
        session = nil
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: any Error) {
        if let nfc = error as? NFCReaderError,
           nfc.code == .readerSessionInvalidationErrorUserCanceled || nfc.code == .readerSessionInvalidationErrorFirstNDEFTagRead {
            finish(.success(nil))
        } else {
            finish(.failure(error))
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first else { return }
        session.connect(to: tag) { [weak self] error in
            guard let self else { return }
            if let error {
                session.invalidate(errorMessage: error.localizedDescription)
                return
            }
            guard let uid = Self.identifier(of: tag) else {
                session.invalidate(errorMessage: Failure.unsupportedCard.localizedDescription)
                return
            }
            session.alertMessage = self.success
            session.invalidate()
            self.finish(.success(uid))
        }
    }

    private static func identifier(of tag: NFCTag) -> String? {
        let bytes: Data
        switch tag {
        case .miFare(let card): bytes = card.identifier
        case .iso7816(let card): bytes = card.identifier
        case .iso15693(let card): bytes = card.identifier
        case .feliCa(let card): bytes = card.currentIDm
        @unknown default: return nil
        }
        return bytes.map { String(format: "%02X", $0) }.joined()
    }
}
