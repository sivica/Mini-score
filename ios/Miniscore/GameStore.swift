import SwiftUI

@MainActor
final class GameStore: ObservableObject {
    @Published private(set) var feed: GameFeed?
    @Published private(set) var selectedID: String?
    @Published private(set) var isRefreshing = false
    @Published private(set) var message = "Loading games…"
    @Published private(set) var checkedAt: Date?
    @Published private(set) var isSaved = true
    private let repository: FeedRepository
    private let snapshot: GameFeed?
    private var started = false
    private var hasCachedFeed = false

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var directory = support.appendingPathComponent("Miniscore", isDirectory: true)
        var transport: any FeedTransport = HTTPFeedTransport()
        let data = Bundle.main.url(forResource: "SampleGames", withExtension: "json")
            .flatMap { try? Data(contentsOf: $0) }
        snapshot = data.flatMap { try? GameFeed.decode($0) }
        #if DEBUG
        if ProcessInfo.processInfo.environment["MINISCORE_UI_TEST"] == "1", let data {
            directory = support.appendingPathComponent("Miniscore-UI-Test", isDirectory: true)
            transport = PreviewTransport(data: data,
                offline: ProcessInfo.processInfo.environment["MINISCORE_TEST_OFFLINE"] == "1")
        }
        #endif
        repository = FeedRepository(directory: directory, transport: transport)
    }

    var selectedGame: Game? { feed?.games.first { $0.id == selectedID } }

    func start() async {
        guard !started else { return }
        started = true
        let cached = await repository.cached()
        hasCachedFeed = cached != nil
        feed = cached?.feed ?? snapshot
        checkedAt = cached?.checkedAt
        if let feed {
            selectedID = GameSelection.resolve(selected: await repository.savedSelection(), previousDefault: nil, feed: feed)
            message = cached == nil ? "Bundled snapshot · pull to refresh" : "Showing saved games"
        }
        await refresh()
    }

    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            let current = try await repository.refresh()
            selectedID = GameSelection.resolve(selected: selectedID, previousDefault: feed?.defaultGameId, feed: current.feed)
            feed = current.feed
            hasCachedFeed = true
            checkedAt = current.checkedAt
            isSaved = false
            message = "Games updated"
            if let selectedID { try? await repository.saveSelection(selectedID) }
        } catch is CancellationError {
            // A cancelled lifecycle task must not replace the user's current view.
        } catch {
            isSaved = true
            let offline = (error as? URLError)?.code == .notConnectedToInternet
            let source = hasCachedFeed ? "saved games" : "bundled snapshot"
            message = feed == nil ? "Couldn’t load games. Try again." :
                offline ? "Offline · showing \(source)" : "Couldn’t refresh. Showing \(source)."
        }
    }

    func select(_ game: Game) {
        selectedID = game.id
        Task { try? await repository.saveSelection(game.id) }
    }
}

#if DEBUG
private struct PreviewTransport: FeedTransport {
    let data: Data
    let offline: Bool
    func fetch(ifNoneMatch: String?) async throws -> FeedDownload {
        if offline { throw URLError(.notConnectedToInternet) }
        return .updated(data, etag: "ui-test-fixture")
    }
}
#endif
