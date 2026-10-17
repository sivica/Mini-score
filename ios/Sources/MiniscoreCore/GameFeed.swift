import Foundation

public enum FeedError: Error, Equatable, Sendable {
    case invalid(String)
    case tooLarge
    case http(Int)
    case missingCache
}

public struct GameFeed: Codable, Equatable, Sendable {
    public let schemaVersion: Int
    public let defaultGameId: String
    public let games: [Game]
    public static let maximumBytes = 2 * 1024 * 1024

    public static func decode(_ data: Data) throws -> GameFeed {
        guard data.count <= maximumBytes else { throw FeedError.tooLarge }
        // JSONDecoder accepts integral floating-point literals as Int. The feed
        // contract requires integer literals, including for schemaVersion.
        var inString = false
        var escaped = false
        let bytes = Array(data)
        var index = 0
        while index < bytes.count {
            let byte = bytes[index]
            if inString {
                if escaped { escaped = false }
                else if byte == 92 { escaped = true }
                else if byte == 34 { inString = false }
            } else if byte == 34 {
                inString = true
            } else if byte == 45 || (48...57).contains(byte) {
                let start = index
                while index < bytes.count && ![9, 10, 13, 32, 44, 93, 125].contains(bytes[index]) { index += 1 }
                let token = String(decoding: bytes[start..<index], as: UTF8.self)
                try require(token.range(of: "^(0|[1-9][0-9]*)$", options: .regularExpression) != nil,
                            "Expected non-negative JSON integer")
                continue
            }
            index += 1
        }
        let feed = try JSONDecoder().decode(Self.self, from: data)
        try feed.validate()
        return feed
    }

    public func validate() throws {
        try require(schemaVersion == 1, "Unsupported feed version")
        try require((1...100).contains(games.count), "Expected 1–100 games")
        try require(Set(games.map(\.id)).count == games.count, "Duplicate game IDs")
        try require(games.contains { $0.id == defaultGameId }, "Missing default game")
        for game in games { try game.validate() }
    }
}

public struct Game: Codable, Identifiable, Equatable, Sendable {
    public let id: String
    public let date: String
    public let competition: String
    public let status: GameStatus
    public let tipoffUTC: String?
    public let venue: String?
    public let away: Team
    public let home: Team

    public var scoreText: String {
        status == .scheduled ? "— – —" : "\(away.score) – \(home.score)"
    }

    public func matches(_ query: String) -> Bool {
        let content = "\(away.code) \(away.name) \(home.code) \(home.name) \(date) \(competition)".lowercased()
        return query.lowercased().split(whereSeparator: \.isWhitespace).allSatisfy { content.contains($0) }
    }

    func validate() throws {
        try text(id)
        try require(!["nyk_cle", "lal_gsw", "bos_mia"].contains(id), "Reserved demo ID")
        try text(competition)
        try require(Self.parsedDate(date) != nil, "Invalid game date")
        if let venue { try text(venue) }
        if let tipoffUTC {
            try require(ISO8601DateFormatter().date(from: tipoffUTC) != nil, "Invalid tipoff")
        }
        try require(away.code != home.code, "Teams must differ")
        try away.validate(final: status == .finished)
        try home.validate(final: status == .finished)
        try require(away.quarters.count == home.quarters.count, "Mismatched quarters")
        let players = away.players + home.players
        try require(Set(players.map(\.id)).count == players.count, "Duplicate player IDs")
    }

    public static func parsedDate(_ value: String) -> Date? {
        guard value.range(of: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", options: .regularExpression) != nil else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        guard let date = formatter.date(from: value), formatter.string(from: date) == value else { return nil }
        return date
    }

    public func dateLabel(compact: Bool = false) -> String {
        guard let parsed = Self.parsedDate(date) else { return date }
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.setLocalizedDateFormatFromTemplate(compact ? "EEEMMMd" : "EEEEMMMMd")
        return formatter.string(from: parsed)
    }
}

public enum GameStatus: String, Codable, Sendable {
    case scheduled = "SCHEDULED", live = "LIVE", finished = "FINISHED"
    case postponed = "POSTPONED", cancelled = "CANCELLED"
    public var label: String {
        switch self {
        case .scheduled: "Scheduled"
        case .live: "Live"
        case .finished: "Final"
        case .postponed: "Postponed"
        case .cancelled: "Cancelled"
        }
    }
}

public struct Team: Codable, Equatable, Sendable {
    public let code: String
    public let name: String
    public let score: Int
    public let quarters: [Int]
    public let players: [Player]
    enum CodingKeys: CodingKey { case code, name, score, quarters, players }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        code = try container.decode(String.self, forKey: .code)
        name = try container.decode(String.self, forKey: .name)
        score = try container.decode(Int.self, forKey: .score)
        players = try container.decode([Player].self, forKey: .players)
        quarters = container.contains(.quarters) ? try container.decode([Int].self, forKey: .quarters) : []
    }

    public var starters: [Player] { players.filter(\.starter) }
    public var substitutes: [Player] {
        players.filter { !$0.starter }.sorted {
            if $0.stats.playedSeconds != $1.stats.playedSeconds { return $0.stats.playedSeconds > $1.stats.playedSeconds }
            return $0.name.localizedStandardCompare($1.name) == .orderedAscending
        }
    }

    func validate(final: Bool) throws {
        try require(code.range(of: "^[A-Z]{2,4}$", options: .regularExpression) != nil, "Invalid team code")
        try text(name)
        try bounded(score, 500)
        try require(quarters.count <= 15 && players.count <= 30, "Too many periods or players")
        for quarter in quarters { try bounded(quarter, 100) }
        for player in players { try player.validate() }
        try require(starters.count <= 5, "More than five starters")
        if final {
            try require(!players.isEmpty && players.reduce(0) { $0 + $1.stats.points } == score,
                        "Player points do not match final score")
            try require(!quarters.isEmpty && quarters.reduce(0, +) == score, "Quarters do not match final score")
        }
    }
}

public struct Player: Codable, Identifiable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let number: String
    public let starter: Bool
    public let stats: PlayerStats
    func validate() throws {
        try text(id); try text(name); try text(number)
        try stats.validate()
    }
}

public struct PlayerStats: Codable, Equatable, Sendable {
    public let points: Int, rebounds: Int, assists: Int, steals: Int, blocks: Int, turnovers: Int
    public let fgMade: Int, fgAttempted: Int, minutes: Int, seconds: Int
    public var playedSeconds: Int { minutes * 60 + seconds }
    public var minutesLabel: String { String(format: "%d:%02d", minutes, seconds) }
    public var primary: [(String, Int)] { [("PTS", points), ("REB", rebounds), ("AST", assists), ("FGM", fgMade), ("FGA", fgAttempted)] }
    func validate() throws {
        try bounded(points, 200)
        for value in [rebounds, assists, steals, blocks, turnovers, fgMade, fgAttempted, minutes] { try bounded(value, 100) }
        try bounded(seconds, 59)
        try require(fgMade <= fgAttempted, "FGM exceeds FGA")
    }
}

public enum GameSelection {
    public static func resolve(selected: String?, previousDefault: String?, feed: GameFeed) -> String {
        if selected == previousDefault { return feed.defaultGameId }
        if let selected, feed.games.contains(where: { $0.id == selected }) { return selected }
        return feed.defaultGameId
    }
}

private func require(_ condition: Bool, _ message: String) throws {
    if !condition { throw FeedError.invalid(message) }
}
private func text(_ value: String) throws {
    try require(!value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && value.count <= 200,
                "Invalid text")
}
private func bounded(_ value: Int, _ maximum: Int) throws {
    try require((0...maximum).contains(value), "Invalid statistic")
}
