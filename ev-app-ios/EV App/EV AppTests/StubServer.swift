//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
@testable import EV_App

struct RecordedRequest: Sendable {
    let method: String
    let url: URL
    let headers: [String: String]
    let body: Data?

    var path: String { url.path() }

    var query: [String: String] {
        var values: [String: String] = [:]
        URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.forEach { values[$0.name] = $0.value }
        return values
    }

    var json: [String: Any]? {
        body.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
    }

    func header(_ name: String) -> String? {
        headers.first { $0.key.caseInsensitiveCompare(name) == .orderedSame }?.value
    }
}

struct StubReply: Sendable {
    let status: Int
    let body: Data

    static func json(_ text: String, status: Int = 200) -> StubReply {
        StubReply(status: status, body: Data(text.utf8))
    }

    static func fixture(_ name: String) -> StubReply {
        StubReply(status: 200, body: (try? TestData.fixture(name)) ?? Data())
    }

    static let success = json(#"{"success":true}"#)
}

final class StubServer: @unchecked Sendable {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var servers: [String: StubServer] = [:]

    let host = "\(UUID().uuidString.lowercased()).stub.invalid"
    private let lock = NSLock()
    private var recorded: [RecordedRequest] = []
    private let handler: @Sendable (RecordedRequest) -> StubReply?
    private let handlesCsrf: Bool

    init(handlesCsrf: Bool = true, handler: @escaping @Sendable (RecordedRequest) -> StubReply?) {
        self.handler = handler
        self.handlesCsrf = handlesCsrf
        Self.lock.withLock { Self.servers[host] = self }
    }

    var baseURL: URL { URL(string: "https://\(host)")! }

    var requests: [RecordedRequest] { lock.withLock { recorded } }

    var calls: [String] { requests.filter { $0.path != "/api/v1/auth/csrf" }.map { "\($0.method) \($0.path)" } }

    func last(_ path: String) -> RecordedRequest? {
        requests.last { $0.path == path }
    }

    func client() -> APIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return APIClient(baseURL: baseURL, configuration: configuration)
    }

    func api() -> EVServerAPI {
        EVServerAPI(client: client())
    }

    static func server(for host: String?) -> StubServer? {
        lock.withLock { host.flatMap { servers[$0] } }
    }

    fileprivate func respond(to request: RecordedRequest) -> StubReply? {
        lock.withLock { recorded.append(request) }
        if handlesCsrf, request.path == "/api/v1/auth/csrf" {
            return .json(#"{"token":"csrf-1","headerName":"X-XSRF-TOKEN"}"#)
        }
        return handler(request)
    }
}

final class StubURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url, let server = StubServer.server(for: url.host()) else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotFindHost))
            return
        }
        var body = request.httpBody
        if body == nil, let stream = request.httpBodyStream {
            stream.open()
            var data = Data()
            var buffer = [UInt8](repeating: 0, count: 16_384)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                data.append(buffer, count: count)
            }
            stream.close()
            body = data
        }
        let recorded = RecordedRequest(method: request.httpMethod ?? "GET", url: url, headers: request.allHTTPHeaderFields ?? [:], body: body)
        guard let reply = server.respond(to: recorded) else {
            client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
            return
        }
        let response = HTTPURLResponse(url: url, statusCode: reply.status, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: reply.body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
