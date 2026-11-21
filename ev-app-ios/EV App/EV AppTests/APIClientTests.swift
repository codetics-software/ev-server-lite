//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct APIClientTests {
    private struct Echo: Decodable, Equatable {
        let value: String
    }

    @Test func getSendsLanguageAndJSONHeadersWithoutCsrf() async throws {
        let server = StubServer { _ in .json(#"{"value":"ok"}"#) }
        let client = server.client()
        let echo: Echo = try await client.get("api/v1/workspace")
        #expect(echo == Echo(value: "ok"))
        let request = try #require(server.requests.first)
        #expect(server.requests.count == 1)
        #expect(request.method == "GET")
        #expect(request.path == "/api/v1/workspace")
        #expect(request.header("Accept") == "application/json")
        #expect(request.header("Accept-Language") == AppLanguage.current)
        #expect(request.header("Authorization") == nil)
        #expect(request.header("X-XSRF-TOKEN") == nil)
    }

    @Test func bearerTokenIsSentOnceSet() async throws {
        let server = StubServer { _ in .json(#"{"value":"ok"}"#) }
        let client = server.client()
        client.bearerToken = "abc"
        #expect(client.bearerToken == "abc")
        let _: Echo = try await client.get("api/v1/auth/me")
        #expect(server.requests.last?.header("Authorization") == "Bearer abc")
        client.bearerToken = nil
        let _: Echo = try await client.get("api/v1/auth/me")
        #expect(server.requests.last?.header("Authorization") == nil)
    }

    @Test func queryDropsMissingValuesAndIsSorted() async throws {
        let server = StubServer { _ in .json(#"{"value":"ok"}"#) }
        let _: Echo = try await server.client().get("api/v1/transactions", query: ["status": "active", "stationId": nil, "limit": "10"], headers: ["X-Charge-Token": "t"])
        let request = try #require(server.requests.first)
        #expect(request.url.query() == "limit=10&status=active")
        #expect(request.header("X-Charge-Token") == "t")
    }

    @Test func mutationsFetchAndReuseTheCsrfToken() async throws {
        let server = StubServer { _ in .json(#"{"value":"saved"}"#) }
        let client = server.client()
        let first: Echo = try await client.post("api/v1/account/cars", body: ["name": "Zoé"])
        let _: Echo = try await client.put("api/v1/account/cars/1", body: ["name": "Zoé bleue"])
        let _: Echo = try await client.delete("api/v1/account/cars/1")
        #expect(first.value == "saved")
        #expect(server.requests.map(\.method) == ["GET", "POST", "PUT", "DELETE"])
        #expect(server.requests.first?.path == "/api/v1/auth/csrf")
        for request in server.requests.dropFirst() {
            #expect(request.header("X-XSRF-TOKEN") == "csrf-1")
        }
        let post = try #require(server.requests.first { $0.method == "POST" })
        #expect(post.header("Content-Type") == "application/json")
        #expect(post.json?["name"] as? String == "Zoé")
        let delete = try #require(server.requests.last)
        #expect(delete.header("Content-Type") == nil)
    }

    @Test func rejectedCsrfIsRefreshedAndRetriedOnce() async throws {
        let attempts = Counter()
        let server = StubServer { request in
            guard request.method == "POST" else { return nil }
            return attempts.increment() == 1 ? .json("{}", status: 403) : .json(#"{"value":"ok"}"#)
        }
        let echo: Echo = try await server.client().post("api/v1/auth/signout")
        #expect(echo.value == "ok")
        #expect(server.requests.map(\.method) == ["GET", "POST", "GET", "POST"])
    }

    @Test func persistentForbiddenBecomesAServerError() async throws {
        let server = StubServer { request in
            request.method == "POST" ? .json(#"{"status":403,"detail":"Interdit.","code":"forbidden"}"#, status: 403) : nil
        }
        await #expect(throws: APIError.server(status: 403, message: "Interdit.", code: "forbidden")) {
            let _: Echo = try await server.client().post("api/v1/auth/signout")
        }
        #expect(server.requests.filter { $0.method == "POST" }.count == 2)
    }

    @Test func forbiddenReadsAreNotRetried() async throws {
        let server = StubServer { _ in .json("{}", status: 403) }
        await #expect(throws: APIError.server(status: 403, message: nil, code: nil)) {
            let _: Echo = try await server.client().get("api/v1/account")
        }
        #expect(server.requests.count == 1)
    }

    @Test func unauthorizedMeansExpiredOnlyWithASession() async throws {
        let server = StubServer { _ in .json(#"{"status":401,"title":"Unauthorized","detail":"Identifiants incorrects."}"#, status: 401) }
        let client = server.client()
        await #expect(throws: APIError.server(status: 401, message: "Identifiants incorrects.", code: nil)) {
            let _: Echo = try await client.get("api/v1/auth/me")
        }
        client.bearerToken = "expired"
        await #expect(throws: APIError.unauthorized) {
            let _: Echo = try await client.get("api/v1/auth/me")
        }
    }

    @Test func serverErrorsWithoutProblemDetailKeepTheStatus() async throws {
        let server = StubServer { _ in StubReply(status: 502, body: Data("<html>".utf8)) }
        await #expect(throws: APIError.server(status: 502, message: nil, code: nil)) {
            let _: Echo = try await server.client().get("api/v1/account")
        }
    }

    @Test func transportFailuresAreReportedAsOffline() async throws {
        let server = StubServer { _ in nil }
        await #expect(throws: APIError.offline) {
            let _: Echo = try await server.client().get("api/v1/account")
        }
    }

    @Test func undecodableBodiesAreInvalidResponses() async throws {
        let server = StubServer { _ in .json(#"{"unexpected":1}"#) }
        await #expect(throws: APIError.invalidResponse) {
            let _: Echo = try await server.client().get("api/v1/account")
        }
    }

    @Test func rawDataAndUploadsKeepTheirBytes() async throws {
        let jpeg = Data([0xFF, 0xD8, 0xFF, 0xE0, 0x01])
        let server = StubServer { request in
            request.method == "GET" ? StubReply(status: 200, body: jpeg) : .json(#"{"value":"stored"}"#)
        }
        let client = server.client()
        #expect(try await client.data("api/v1/photos/1") == jpeg)
        let stored: Echo = try await client.upload("api/v1/account/stations/PAR-01/photos", data: jpeg, contentType: "image/jpeg")
        #expect(stored.value == "stored")
        let upload = try #require(server.requests.last)
        #expect(upload.header("Content-Type") == "image/jpeg")
        #expect(upload.body == jpeg)
        #expect(upload.path == "/api/v1/account/stations/PAR-01/photos")
    }
}

final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var value = 0

    func increment() -> Int {
        lock.withLock {
            value += 1
            return value
        }
    }
}
