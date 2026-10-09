# Update Miniscore games without rebuilding the APK

Install **Miniscore 1.4 once**. Its date, game selector, scores, rosters and player stats then come from a public GitHub JSON feed (existing 1.3 installations also support feed updates):

- [Edit the live games.json](https://github.com/sivica/Mini-score/edit/game-data/games.json)
- [View the live feed](https://raw.githubusercontent.com/sivica/Mini-score/game-data/games.json)

The `game-data` branch is the published data source. The copy of `games.json` on the app development branch is a sample and test fixture; editing that copy does not publish to installed apps. GitHub serves the JSON directly, so GitHub Pages setup is unnecessary.

## Tomorrow's game

1. Open the live editor above. Keep `schemaVersion` at `1`.
2. Add a game to `games`, using a unique `id`. Keep earlier games to let users select them.
3. Set `date` to the actual NBA game date (`YYYY-MM-DD`). The app derives the displayed weekday and date from this field. Changing the phone's date does not relabel yesterday's result.
4. Set `competition`, `venue`, and the two teams. Set `defaultGameId` to the new game's `id` to open it by default.
5. Before tipoff use `status: "SCHEDULED"`, `score: 0`, `players: []` and `quarters: []` for both teams. Optionally add `tipoffUTC`, for example `"2026-10-10T00:00:00Z"`. Version 1.4 displays that time as **8:00 PM ET** in the selector. The app displays dashes for scheduled scores and a message when stats are unavailable.
6. When verified stats are available, add the players, scores and quarter scores; set `status` to `LIVE` or `FINISHED` as appropriate. Do not copy yesterday's player stats into a new game.
7. Click **Commit changes** and commit directly to **game-data**. Reopen the app, tap the refresh icon, or pull down the lineup to download the published data. GitHub may take a few minutes to serve a new revision.

You can also ask Codex to update this file with a sourced game, or use GitHub Desktop / git to commit data edits to `game-data`. The feed is manual; no sports provider or daily automation is configured.

## Game fields

Each game requires `id`, `date`, `competition`, `status`, `away` and `home`. `venue`, `tipoffUTC` (UTC timestamp in `YYYY-MM-DDTHH:mm:ssZ` format), and a `sources` list are optional. Supported statuses: `SCHEDULED`, `LIVE`, `FINISHED`, `POSTPONED`, `CANCELLED`. Missing or unreadable tipoff times show **Scheduled**. The date remains the declared NBA game date regardless of the phone's timezone.

Each team requires `code` (2–4 uppercase letters), `name`, `score`, and `players`. Optional fields: `shortName`, `primaryColor`, `secondaryColor` (`#RRGGBB`), `logoUrl`, and `quarters` (array of period scores). Both quarter arrays must have the same length. Scores remain away on the left, home on the right.

Each player requires a stable unique `id`, `name`, `number` (string), `starter` (boolean), and a `stats` object with all these integer fields:

```json
{
  "points": 0, "rebounds": 0, "assists": 0,
  "steals": 0, "blocks": 0, "turnovers": 0,
  "fgMade": 0, "fgAttempted": 0,
  "minutes": 0, "seconds": 0
}
```

An optional `portraitUrl` loads a player image. Use HTTPS PNG, JPEG or WebP URLs for portraits and team `logoUrl`; the image loader in this app does not decode SVG. Use stable NBA player IDs for new player records rather than jersey numbers. The initial Philadelphia–Brooklyn feed retains its existing IDs for bundled offline photo matching.

Team logos and all 30 headshots for the initial game are bundled. Other downloaded images use Coil's image cache; a failed photo download falls back to initials, and a failed team-logo download shows the team code. A feed can introduce new players and teams without another APK.

## Refresh and offline behavior

The app checks the feed when opened or resumed. Pull to refresh and the header refresh button both request another download. A small spinner shows the request; a failed refresh displays **“Couldn’t refresh. Showing saved games.”**

Every valid feed is saved to internal storage before the next launch. Offline, the app loads the last saved feed; on a first launch without a connection it shows the bundled October 8 game. An invalid JSON file, unsupported schema, duplicate ID, negative or fractional stat, invalid date, or insecure image URL is rejected without replacing the working cache. Up to 100 games and a 2 MiB feed are supported.

Changing `defaultGameId` advances users who were viewing the previous default. Users browsing a different retained game keep their selection. Version 1.4 lists only real games; the demo games and simulator have been removed. A feed refresh dismisses any open player sheet so details cannot retain an older box score.

App layout or schema changes still require an APK update. Data edits within this schema do not. The separate static web demo still uses `data.js`.
