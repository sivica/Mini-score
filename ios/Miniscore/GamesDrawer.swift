import SwiftUI

struct GamesDrawer: View {
    @ObservedObject var store: GameStore
    let close: () -> Void
    @State private var search = ""
    @Environment(\.dynamicTypeSize) private var dynamicType

    private var filtered: [Game] { store.feed?.games.filter { $0.matches(search) } ?? [] }
    private var days: [String] { Array(Set(filtered.map(\.date))).sorted(by: >) }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    ForEach(days, id: \.self) { day in
                        let games = filtered.filter { $0.date == day }
                        if let game = games.first {
                            Text(game.dateLabel()).font(.caption).foregroundStyle(Palette.secondary)
                                .padding(.top, 6).accessibilityAddTraits(.isHeader)
                        }
                        ForEach(games) { game in
                            Button { store.select(game); close() } label: {
                                VStack(spacing: 7) {
                                    if dynamicType.isAccessibilitySize {
                                        Text(game.scoreText).font(.title3).monospacedDigit()
                                        HStack { Text(game.away.code); Spacer(); Text(game.home.code) }
                                    } else {
                                        HStack {
                                            Text(game.away.code).frame(maxWidth: .infinity)
                                            Text(game.scoreText).font(.title3).monospacedDigit()
                                            Text(game.home.code).frame(maxWidth: .infinity)
                                        }
                                    }
                                    Text(game.status.label).font(.caption).foregroundStyle(Palette.secondary)
                                }
                                .foregroundStyle(Palette.text).padding(18).frame(maxWidth: .infinity)
                                .background(store.selectedID == game.id ? Palette.selection : Palette.surface,
                                            in: RoundedRectangle(cornerRadius: 16))
                                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.divider, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(game.away.code) \(game.away.score), \(game.home.code) \(game.home.score), \(game.status.label), \(game.dateLabel())")
                            .accessibilityAddTraits(store.selectedID == game.id ? [.isSelected] : [])
                            .accessibilityIdentifier("select-game-\(game.id)")
                        }
                    }
                    if filtered.isEmpty { ContentUnavailableView.search(text: search) }
                    Link("Privacy policy", destination: URL(string: "https://sivica.github.io/Mini-score/privacy/")!)
                        .font(.subheadline).frame(maxWidth: .infinity, minHeight: 44).padding(.top, 16)
                    Link("Contact support", destination: URL(string: "mailto:s.ivica@gmail.com")!)
                        .font(.subheadline).frame(maxWidth: .infinity, minHeight: 44)
                }
                .padding(16).frame(maxWidth: 600).frame(maxWidth: .infinity)
            }
            .background(Palette.background)
            .navigationTitle("Miniscore").navigationBarTitleDisplayMode(.inline)
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search games")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { MiniscoreLogo(size: 30) }
                ToolbarItem(placement: .confirmationAction) { Button("Done", action: close) }
            }
        }
    }
}
