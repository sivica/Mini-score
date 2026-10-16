import SwiftUI

struct GamesView: View {
    @ObservedObject var store: GameStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingGames = false
    @State private var playerDetail: SelectedPlayer?

    var body: some View {
        NavigationStack {
            ScrollViewReader { scroll in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        Color.clear.frame(height: 0).id("game-top")
                        if store.isSaved {
                            Text(store.message).font(.caption).foregroundStyle(Palette.secondary)
                                .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                                .padding(10).background(Palette.surface)
                                .accessibilityIdentifier("saved-feed-banner")
                        }
                        if let game = store.selectedGame {
                            ScoreboardView(game: game).id(game.id)
                            playerRows(game: game, away: game.away.starters, home: game.home.starters)
                            if !game.away.substitutes.isEmpty || !game.home.substitutes.isEmpty {
                                sectionHeader(game: game, title: "Substitutes")
                                playerRows(game: game, away: game.away.substitutes, home: game.home.substitutes)
                            }
                            if game.away.players.isEmpty && game.home.players.isEmpty {
                                ContentUnavailableView(game.status == .scheduled ? "Game hasn’t started" : "Stats unavailable",
                                    systemImage: "basketball", description: Text("Player statistics will appear after the game."))
                            }
                        } else {
                            ContentUnavailableView {
                                Label("Miniscore", systemImage: "basketball")
                            } description: { Text(store.message) } actions: {
                                Button("Try again") { Task { await store.refresh() } }
                            }
                            .padding(.top, 60)
                        }
                        VStack(spacing: 5) {
                            Text(store.message).accessibilityIdentifier("feed-status")
                            if let checkedAt = store.checkedAt {
                                Text("Last checked \(checkedAt.formatted(date: .abbreviated, time: .shortened))")
                            }
                        }
                        .font(.caption).foregroundStyle(Palette.secondary)
                        .multilineTextAlignment(.center).padding(20)
                        .accessibilityElement(children: .combine)
                    }
                    .frame(maxWidth: 760)
                    .frame(maxWidth: .infinity)
                }
                .accessibilityIdentifier("game-stats")
                .background(Palette.background)
                .refreshable { await store.refresh() }
                .navigationBarTitleDisplayMode(.inline)
                .toolbarBackground(Palette.background, for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { showingGames = true } label: { Image(systemName: "line.3.horizontal") }
                            .frame(minWidth: 44, minHeight: 44)
                            .accessibilityLabel("Open games drawer")
                            .accessibilityIdentifier("open-games")
                    }
                    ToolbarItem(placement: .principal) { MiniscoreLogo() }
                    if store.isRefreshing {
                        ToolbarItem(placement: .topBarTrailing) {
                            ProgressView().accessibilityLabel("Refreshing games")
                        }
                    }
                }
                .onChange(of: store.selectedID) { _, _ in scroll.scrollTo("game-top", anchor: .top) }
            }
            .sheet(isPresented: $showingGames) {
                GamesDrawer(store: store) { showingGames = false }
            }
            .sheet(item: $playerDetail) { selected in
                PlayerDetailView(player: selected.player, team: selected.team)
            }
        }
        .task { await store.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await store.refresh() } }
        }
        .onChange(of: store.selectedID) { _, _ in playerDetail = nil }
        .onChange(of: store.feed) { _, _ in playerDetail = nil }
    }

    private func playerRows(game: Game, away: [Player], home: [Player]) -> some View {
        ForEach(0..<max(away.count, home.count), id: \.self) { index in
            PlayerPairView(away: index < away.count ? away[index] : nil,
                           home: index < home.count ? home[index] : nil,
                           awayCode: game.away.code, homeCode: game.home.code) { player, code in
                playerDetail = SelectedPlayer(player: player, team: code)
            }
        }
    }

    private func sectionHeader(game: Game, title: String) -> some View {
        HStack {
            Text(game.away.code)
            Spacer()
            Text(title).foregroundStyle(Palette.text)
            Spacer()
            Text(game.home.code)
        }
        .font(.caption).foregroundStyle(Palette.secondary)
        .padding(.horizontal, 14).padding(.vertical, 10).background(Palette.header)
        .accessibilityAddTraits(.isHeader)
    }
}

private struct SelectedPlayer: Identifiable {
    let player: Player
    let team: String
    var id: String { team + player.id }
}

struct ScoreboardView: View {
    let game: Game
    @Environment(\.dynamicTypeSize) private var dynamicType
    @State private var expanded = false
    @ScaledMetric(relativeTo: .title) private var scoreSize = 32.0

    var body: some View {
        VStack(spacing: 5) {
            VStack(spacing: 4) {
                Text(game.competition)
                Text(game.dateLabel(compact: true))
            }
            .font(.subheadline).foregroundStyle(Palette.secondary)
            HStack(alignment: .center) {
                if !dynamicType.isAccessibilitySize { Text(game.away.code).font(.headline).frame(maxWidth: .infinity) }
                Text(game.scoreText).font(.system(size: scoreSize, weight: .medium, design: .rounded))
                    .monospacedDigit().lineLimit(1).minimumScaleFactor(0.6).layoutPriority(1)
                    .accessibilityIdentifier("score")
                if !dynamicType.isAccessibilitySize { Text(game.home.code).font(.headline).frame(maxWidth: .infinity) }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(game.away.code), \(game.away.score). \(game.home.code), \(game.home.score). \(game.status.label)")
            if dynamicType.isAccessibilitySize {
                HStack { Text(game.away.code); Spacer(); Text(game.home.code) }.font(.headline)
            }
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 7) {
                    Text(game.status.label)
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").foregroundStyle(Palette.accent)
                }
                .font(.subheadline).frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(game.status.label), \(expanded ? "hide" : "show") game details")
            .accessibilityIdentifier("game-details")
            if expanded {
                if let venue = game.venue { Text(venue).font(.caption).foregroundStyle(Palette.secondary) }
                if !game.away.quarters.isEmpty { QuarterScores(game: game) }
            }
        }
        .foregroundStyle(Palette.text)
        .padding(.horizontal, 18).padding(.top, 14).padding(.bottom, 6)
        .frame(maxWidth: .infinity).background(Palette.header)
    }
}

private struct QuarterScores: View {
    let game: Game
    var body: some View {
        ScrollView(.horizontal) {
        Grid(horizontalSpacing: 12, verticalSpacing: 8) {
            GridRow {
                Text("Team")
                ForEach(game.away.quarters.indices, id: \.self) { index in
                    Text(index < 4 ? "Q\(index + 1)" : "OT\(index - 3)")
                }
            }.foregroundStyle(Palette.secondary)
            ForEach([game.away, game.home], id: \.code) { team in
                GridRow {
                    Text(team.code)
                    ForEach(team.quarters.indices, id: \.self) { index in Text("\(team.quarters[index])") }
                }
            }
        }
        .font(.caption).monospacedDigit().padding(.vertical, 12)
        .frame(minWidth: 280)
        }
    }
}

private struct PlayerPairView: View {
    let away: Player?
    let home: Player?
    let awayCode: String
    let homeCode: String
    let select: (Player, String) -> Void
    @Environment(\.dynamicTypeSize) private var dynamicType

    var body: some View {
        Group {
            if dynamicType.isAccessibilitySize {
                VStack(spacing: 20) {
                    if let away { card(away, code: awayCode, mirrored: false, showTeam: true) }
                    if let home { card(home, code: homeCode, mirrored: false, showTeam: true) }
                }
            } else {
                HStack(alignment: .top, spacing: 10) {
                    if let away { card(away, code: awayCode, mirrored: false) }
                    else { Color.clear.frame(maxWidth: .infinity).accessibilityHidden(true) }
                    Rectangle().fill(Palette.divider).frame(width: 1)
                    if let home { card(home, code: homeCode, mirrored: true) }
                    else { Color.clear.frame(maxWidth: .infinity).accessibilityHidden(true) }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 17)
    }

    private func card(_ player: Player, code: String, mirrored: Bool, showTeam: Bool = false) -> some View {
        Button { select(player, code) } label: {
            PlayerCard(player: player, team: showTeam ? code : nil, mirrored: mirrored)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain).frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(player.name), \(code), number \(player.number), \(player.stats.minutes) minutes \(player.stats.seconds) seconds. \(player.stats.points) points, \(player.stats.rebounds) rebounds, \(player.stats.assists) assists, \(player.stats.fgMade) field goals made of \(player.stats.fgAttempted) attempts")
        .accessibilityHint("Show all player statistics")
    }
}

private struct PlayerCard: View {
    let player: Player
    let team: String?
    let mirrored: Bool
    @ScaledMetric(relativeTo: .body) private var valueSize = 14.0
    @ScaledMetric(relativeTo: .caption) private var labelSize = 12.0
    var body: some View {
        VStack(alignment: mirrored ? .trailing : .leading, spacing: 8) {
            if let team { Text(team).font(.caption).foregroundStyle(Palette.secondary) }
            Text(player.name).font(.system(size: valueSize)).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: mirrored ? .trailing : .leading)
            HStack {
                if mirrored { Text(player.stats.minutesLabel).monospacedDigit(); Spacer(); Text("#\(player.number)") }
                else { Text("#\(player.number)"); Spacer(); Text(player.stats.minutesLabel).monospacedDigit() }
            }
            .font(.system(size: valueSize)).foregroundStyle(Palette.secondary)
            HStack(spacing: 1) {
                ForEach(player.stats.primary, id: \.0) { label, value in
                    VStack(spacing: 5) {
                        Text(label).font(.system(size: labelSize)).foregroundStyle(Palette.secondary)
                        Text("\(value)").font(.system(size: valueSize, weight: .medium)).foregroundStyle(Palette.accent)
                    }
                    .monospacedDigit().frame(maxWidth: .infinity)
                }
            }
        }
        .foregroundStyle(Palette.text)
    }
}

struct PlayerDetailView: View {
    let player: Player
    let team: String
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent("Team", value: team)
                    LabeledContent("Jersey", value: "#\(player.number)")
                    LabeledContent("Minutes played", value: player.stats.minutesLabel)
                }
                Section("Statistics") {
                    ForEach(player.stats.primary, id: \.0) { label, value in LabeledContent(label, value: "\(value)") }
                    LabeledContent("Steals", value: "\(player.stats.steals)")
                    LabeledContent("Blocks", value: "\(player.stats.blocks)")
                    LabeledContent("Turnovers", value: "\(player.stats.turnovers)")
                }
            }
            .navigationTitle(player.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
