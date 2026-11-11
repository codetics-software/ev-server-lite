//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated final class APIClient: Sendable {
    let baseURL: URL
    private let session: URLSession
    private let tokens: TokenBox

    private final class TokenBox: @unchecked Sendable {
        private let lock = NSLock()
        private var bearer: String?
        private var csrf: CsrfResponse?

        var bearerToken: String? {
            get { lock.withLock { bearer } }
            set { lock.withLock { bearer = newValue } }
        }

        var csrfToken: CsrfResponse? {
            get { lock.withLock { csrf } }
            set { lock.withLock { csrf = newValue } }
        }
    }

    init(baseURL: URL, configuration: URLSessionConfiguration = .default) {
        self.baseURL = baseURL
        configuration.httpCookieStorage = .shared
        configuration.httpCookieAcceptPolicy = .always
        configuration.timeoutIntervalForRequest = 30
        self.session = URLSession(configuration: configuration)
        self.tokens = TokenBox()
    }

    var bearerToken: String? {
        get { tokens.bearerToken }
        set { tokens.bearerToken = newValue }
    }

    func get<Response: Decodable>(_ path: String, query: [String: String?] = [:], headers: [String: String] = [:]) async throws -> Response {
        let data = try await send(method: "GET", path: path, query: query, headers: headers, body: nil, contentType: nil)
        return try decode(data)
    }

    func data(_ path: String) async throws -> Data {
        try await send(method: "GET", path: path, query: [:], headers: [:], body: nil, contentType: nil)
    }

    func post<Response: Decodable>(_ path: String, headers: [String: String] = [:]) async throws -> Response {
        let data = try await send(method: "POST", path: path, query: [:], headers: headers, body: nil, contentType: nil)
        return try decode(data)
    }

    func post<Response: Decodable>(_ path: String, body: some Encodable) async throws -> Response {
        let data = try await send(method: "POST", path: path, query: [:], headers: [:], body: JSONCoding.encoder.encode(body), contentType: "application/json")
        return try decode(data)
    }

    func put<Response: Decodable>(_ path: String, body: some Encodable) async throws -> Response {
        let data = try await send(method: "PUT", path: path, query: [:], headers: [:], body: JSONCoding.encoder.encode(body), contentType: "application/json")
        return try decode(data)
    }

    func delete<Response: Decodable>(_ path: String) async throws -> Response {
        let data = try await send(method: "DELETE", path: path, query: [:], headers: [:], body: nil, contentType: nil)
        return try decode(data)
    }

    func upload<Response: Decodable>(_ path: String, data body: Data, contentType: String) async throws -> Response {
        let data = try await send(method: "POST", path: path, query: [:], headers: [:], body: body, contentType: contentType)
        return try decode(data)
    }

    private func decode<Response: Decodable>(_ data: Data) throws -> Response {
        do {
            return try JSONCoding.decoder.decode(Response.self, from: data)
        } catch {
            throw APIError.invalidResponse
        }
    }

    private func send(
        method: String,
        path: String,
        query: [String: String?],
        headers: [String: String],
        body: Data?,
        contentType: String?,
        retried: Bool = false
    ) async throws -> Data {
        var request = URLRequest(url: try url(path: path, query: query))
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(AppLanguage.current, forHTTPHeaderField: "Accept-Language")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        if let bearer = tokens.bearerToken { request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        if method != "GET" {
            let csrf = try await csrfToken()
            request.setValue(csrf.token, forHTTPHeaderField: csrf.headerName)
        }

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw APIError.offline
        }
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        if (200..<300).contains(http.statusCode) { return data }
        if http.statusCode == 403, method != "GET", !retried {
            tokens.csrfToken = nil
            return try await send(method: method, path: path, query: query, headers: headers, body: body, contentType: contentType, retried: true)
        }
        let problem = try? JSONCoding.decoder.decode(ProblemDetail.self, from: data)
        if http.statusCode == 401, tokens.bearerToken != nil { throw APIError.unauthorized }
        throw APIError.server(status: http.statusCode, message: problem?.detail, code: problem?.code)
    }

    private func csrfToken() async throws -> CsrfResponse {
        if let cached = tokens.csrfToken { return cached }
        let data = try await send(method: "GET", path: "api/v1/auth/csrf", query: [:], headers: [:], body: nil, contentType: nil)
        let csrf = try JSONCoding.decoder.decode(CsrfResponse.self, from: data)
        tokens.csrfToken = csrf
        return csrf
    }

    private func url(path: String, query: [String: String?]) throws -> URL {
        var url = baseURL
        path.split(separator: "/").forEach { url.append(path: String($0)) }
        let items = query.compactMap { key, value in value.map { URLQueryItem(name: key, value: $0) } }
        if !items.isEmpty { url.append(queryItems: items.sorted { $0.name < $1.name }) }
        return url
    }
}
