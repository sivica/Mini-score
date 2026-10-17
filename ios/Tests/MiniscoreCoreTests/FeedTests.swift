import Foundation
import Testing
@testable import MiniscoreCore

private func fixture() throws -> Data {
    try Data(contentsOf: Bundle.module.url(forResource: "games", withExtension: "json", subdirectory: "Fixtures")!)
}
private func modified(_ edit: (inout [String: Any]) -> Void) throws -> Data {
    var object = try JSONSerialization.jsonObject(with: fixture()) as! [String: Any]
    edit(&object)
    return try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
}
private func editAway(_ edit: (inout [String: Any]) -> Void) throws -> Data {
    try modified { root in
        var games = root["games"] as! [[String: Any]]
        var away = games[0]["away"] as! [String: Any]
        edit(&away)
        games[0]["away"] = away
        root["games"] = games
    }
}
private func rejected(_ data: Data) -> Bool { (try? GameFeed.decode(data)) == nil }

@Test func realPublishedFeedAndPresentation() throws {
    let feed = try GameFeed.decode(fixture())
    #expect(feed.games.count == 2)
    #expect(feed.games.allSatisfy { $0.status == .finished })
    #expect(feed.games[0].matches("OKC HOU"))
    #expect(!feed.games[0].matches("MEM"))
    for game in feed.games {
        for team in [game.away, game.home] {
            #expect(team.players.reduce(0) { $0 + $1.stats.points } == team.score)
            let seconds = team.substitutes.map { $0.stats.playedSeconds }
            #expect(seconds == seconds.sorted(by: >))
        }
    }
    let stats = PlayerStats(points: 1, rebounds: 0, assists: 0, steals: 0, blocks: 0,
                            turnovers: 0, fgMade: 0, fgAttempted: 0, minutes: 7, seconds: 3)
    #expect(stats.minutesLabel == "7:03")
    #expect(stats.primary.map(\.0) == ["PTS", "REB", "AST", "FGM", "FGA"])
}

@Test(arguments: ["true", "1.0", "1e0", "\"1\"", "-1", "null"])
func strictIntegers(token: String) throws {
    let data = try modified { $0["schemaVersion"] = "INTEGER_SENTINEL" }
    let text = String(decoding: data, as: UTF8.self).replacingOccurrences(of: "\"INTEGER_SENTINEL\"", with: token)
    #expect(rejected(Data(text.utf8)))
}

@Test(arguments: ["players", "quarters"])
func strictArrays(field: String) throws {
    #expect(rejected(try editAway { $0[field] = NSNull() }))
    #expect(rejected(try editAway { $0[field] = ["unexpected": 1] }))
}

@Test func invalidGamesAndTotals() throws {
    #expect(rejected(try modified { $0["games"] = [] }))
    #expect(rejected(try modified { $0["defaultGameId"] = "absent" }))
    #expect(rejected(try modified { root in
        let games = root["games"] as! [[String: Any]]
        root["games"] = [games[0], games[0]]
    }))
    #expect(rejected(try editAway { $0["score"] = 499 }))
    #expect(Game.parsedDate("2026-02-30") == nil)
    #expect(Game.parsedDate("2026-10-15") != nil)
    #expect(rejected(Data(repeating: 32, count: GameFeed.maximumBytes + 1)))
}

@Test func selectionFollowsDefaultButPreservesBrowsing() throws {
    let feed = try GameFeed.decode(fixture())
    let other = feed.games.first { $0.id != feed.defaultGameId }!.id
    #expect(GameSelection.resolve(selected: other, previousDefault: feed.defaultGameId, feed: feed) == other)
    #expect(GameSelection.resolve(selected: "old", previousDefault: "old", feed: feed) == feed.defaultGameId)
    #expect(GameSelection.resolve(selected: "missing", previousDefault: "old", feed: feed) == feed.defaultGameId)
}

private actor Transport: FeedTransport {
    enum Reply: Sendable { case value(FeedDownload), offline }
    var replies: [Reply]
    var tags: [String?] = []
    init(_ replies: [Reply]) { self.replies = replies }
    func fetch(ifNoneMatch: String?) async throws -> FeedDownload {
        tags.append(ifNoneMatch)
        guard !replies.isEmpty else { throw URLError(.notConnectedToInternet) }
        switch replies.removeFirst() {
        case .value(let value): return value
        case .offline: throw URLError(.notConnectedToInternet)
        }
    }
}
private func directory() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString) }

@Test func rejectedFeedsAndNetworkFailurePreserveCache() async throws {
    let folder = directory()
    defer { try? FileManager.default.removeItem(at: folder) }
    let original = try fixture()
    let nullPlayers = try editAway { $0["players"] = NSNull() }
    let decimal = String(decoding: original, as: UTF8.self)
        .replacingOccurrences(of: "\"schemaVersion\": 1", with: "\"schemaVersion\": 1.0")
    #expect(rejected(Data(decimal.utf8)))
    let transport = Transport([.value(.updated(original, etag: "rev9")),
        .value(.updated(nullPlayers, etag: "bad")), .value(.updated(Data(decimal.utf8), etag: "bad")), .offline])
    let repository = FeedRepository(directory: folder, transport: transport)
    let initial = try await repository.refresh()
    let cache = folder.appendingPathComponent("validated-feed.json")
    let savedBytes = try Data(contentsOf: cache)
    for _ in 0..<3 {
        var didThrow = false
        do { _ = try await repository.refresh() } catch { didThrow = true }
        #expect(didThrow)
        #expect(try Data(contentsOf: cache) == savedBytes)
        let saved = await repository.cached()
        #expect(saved?.feed == initial.feed)
    }
    let tags = await transport.tags
    #expect(tags == [nil, "rev9", "rev9", "rev9"])
    let restarted = FeedRepository(directory: folder, transport: Transport([.offline]))
    let restored = await restarted.cached()
    #expect(restored?.feed == initial.feed)
    try await restarted.saveSelection(initial.feed.games[1].id)
    let selection = await restarted.savedSelection()
    #expect(selection == initial.feed.games[1].id)
}

@Test func conditionalResponseNeedsValidatedCache() async throws {
    let folder = directory()
    defer { try? FileManager.default.removeItem(at: folder) }
    let transport = Transport([.value(.notModified), .value(.updated(try fixture(), etag: "rev9")), .value(.notModified)])
    let repository = FeedRepository(directory: folder, transport: transport)
    do { _ = try await repository.refresh(); Issue.record("304 must not succeed without cache") }
    catch { #expect(error as? FeedError == .missingCache) }
    let initial = try await repository.refresh()
    let checked = try await repository.refresh()
    #expect(initial.feed == checked.feed)
    #expect(checked.checkedAt >= initial.checkedAt)
}
