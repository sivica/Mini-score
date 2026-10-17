import Foundation

public enum FeedDownload: Sendable {
    case updated(Data, etag: String?)
    case notModified
}

public protocol FeedTransport: Sendable {
    func fetch(ifNoneMatch: String?) async throws -> FeedDownload
}

public struct HTTPFeedTransport: FeedTransport {
    public static let endpoint = URL(string: "https://miniscore-api-production.up.railway.app/games")!
    private let session: URLSession
    public init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.urlCache = nil
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 30
        session = URLSession(configuration: config, delegate: FeedRedirectPolicy(), delegateQueue: nil)
    }
    public func fetch(ifNoneMatch: String?) async throws -> FeedDownload {
        var request = URLRequest(url: Self.endpoint, cachePolicy: .reloadIgnoringLocalCacheData)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let ifNoneMatch { request.setValue(ifNoneMatch, forHTTPHeaderField: "If-None-Match") }
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw FeedError.http(0) }
        if http.statusCode == 304 { return .notModified }
        guard http.statusCode == 200 else { throw FeedError.http(http.statusCode) }
        guard response.expectedContentLength <= GameFeed.maximumBytes else { throw FeedError.tooLarge }
        var data = Data()
        for try await byte in bytes {
            guard data.count < GameFeed.maximumBytes else { throw FeedError.tooLarge }
            data.append(byte)
        }
        return .updated(data, etag: http.value(forHTTPHeaderField: "ETag"))
    }
}

private final class FeedRedirectPolicy: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        let approved = request.url?.scheme == "https" && request.url?.host == HTTPFeedTransport.endpoint.host
            && request.url?.user == nil && request.url?.password == nil
        completionHandler(approved ? request : nil)
    }
}

public struct CachedFeed: Sendable {
    public let feed: GameFeed
    public let checkedAt: Date
}

public actor FeedRepository {
    private struct Envelope: Codable {
        let payload: Data
        let etag: String?
        let checkedAt: Date
    }
    private let directory: URL
    private let transport: any FeedTransport
    private var envelope: Envelope?
    public init(directory: URL, transport: any FeedTransport = HTTPFeedTransport()) {
        self.directory = directory
        self.transport = transport
    }
    private var cacheURL: URL { directory.appendingPathComponent("validated-feed.json") }

    public func cached() -> CachedFeed? {
        guard let bytes = try? Data(contentsOf: cacheURL), bytes.count <= GameFeed.maximumBytes * 2,
              let saved = try? JSONDecoder().decode(Envelope.self, from: bytes),
              let feed = try? GameFeed.decode(saved.payload) else { return nil }
        envelope = saved
        return CachedFeed(feed: feed, checkedAt: saved.checkedAt)
    }

    public func refresh() async throws -> CachedFeed {
        if envelope == nil { _ = cached() }
        let download = try await transport.fetch(ifNoneMatch: envelope?.etag)
        let candidate: Envelope
        switch download {
        case let .updated(data, etag):
            _ = try GameFeed.decode(data)
            candidate = Envelope(payload: data, etag: etag, checkedAt: Date())
        case .notModified:
            guard let saved = envelope else { throw FeedError.missingCache }
            candidate = Envelope(payload: saved.payload, etag: saved.etag, checkedAt: Date())
        }
        let feed = try GameFeed.decode(candidate.payload)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(candidate).write(to: cacheURL, options: [.atomic])
        envelope = candidate
        return CachedFeed(feed: feed, checkedAt: candidate.checkedAt)
    }

    public func savedSelection() -> String? {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent("selection.json")) else { return nil }
        return try? JSONDecoder().decode(String.self, from: data)
    }
    public func saveSelection(_ id: String) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(id).write(to: directory.appendingPathComponent("selection.json"), options: [.atomic])
    }
}
