# Game visualizations

This MVP adds two charts for an available `FINISHED` or `LIVE` game's player statistics. Open **Charts** from the displayed game. The session captures native game data; changing filters never fetches data. **Refresh** explicitly requests the latest published feed for that same game. It does not promise live scores or change the selected game to the feed's new default.

Native controls select chart, team, player, metric and sort order. **Show accessible source table** presents the same filtered, sorted data without requiring WebView. Field goals show made, attempted, missed and percentage; the combined percentage is total made divided by total attempts, rather than an average of player percentages. Unavailable counts remain **Unknown**. Zero attempts produce **N/A** for a player's percentage. A combined percentage is Unknown if any relevant count is missing.

## Chart settings imports

Use **Import chart settings** to choose a UTF-8 JSON document. Only settings are imported; HTML, scripts, URLs and replacement statistics are unsupported. Imports are limited to 16 KiB. Invalid imports retain the current chart and data.

`gameId` must exactly match the captured game. `schemaVersion` must be the JSON integer `1`. These examples require replacing `YOUR_GAME_ID` with that game's ID.

```json
{
  "schemaVersion": 1,
  "template": "playerStats",
  "gameId": "YOUR_GAME_ID",
  "team": "both",
  "player": "all",
  "metric": "points",
  "sort": "desc",
  "title": "Player points"
}
```

```json
{
  "schemaVersion": 1,
  "template": "fieldGoals",
  "gameId": "YOUR_GAME_ID",
  "team": "home",
  "player": "all",
  "sort": "desc"
}
```

The field-goal template must omit `metric`; it sorts numeric values by attempts. Supported player-stat metrics are `points`, `rebounds`, `assists`, `steals`, `blocks`, and `turnovers`. `team` is `both`, `home`, or `away`. `player` is `all` or an existing player ID belonging to the selected team. `sort` is `asc`, `desc`, or `name`. Unknown values sort last; ties use name and player ID. Defaults are both teams, all players, points, descending order and no custom title.

The native contract rejects unknown or duplicate fields, reserved inherited-object names, malformed JSON, excessive nesting, coerced integers and invalid game/team/player bindings. Native snapshot payloads are versioned, capped at 256 KiB and contain two teams with at most 40 players each (80 rows, 160 field-goal marks). Titles are limited to 120 characters, IDs to 128, labels to 160 and other strings to 256. Numeric counts and scores are bounded; made field goals cannot exceed attempts. See `VisualizationContract` and its tests for the exact accepted schema.

## Data identity and recovery

Published revision/time, native fetch time, chart capture time and cache origin are shown separately. Unknown publication identity stays unknown. Existing unversioned caches remain readable. Once a versioned publication has been accepted, an older or unversioned response cannot overwrite it. Feed validation and atomic cache writes happen before a publication is accepted.

Failed refreshes keep the last valid chart and source table. If the game disappears from the newest feed, the session stays on its captured game and explains why it was retained. Closing cancels session work and ignores late responses/imports. Rotation retains the session in the ViewModel. Process death returns to the scores screen; saved-chart reopening is excluded from this MVP.

## Renderer boundary

Charts use only the three packaged HTML/CSS/JavaScript assets under an exact HTTPS app-assets origin and GET path allowlist. Unmatched requests return a denial instead of falling through to a network request. Network loads, file/content access, mixed content, popups, downloads, file selection and browser permissions are disabled. A restrictive CSP denies remote connections, framing and executable imports. Player text is inserted with `textContent`.

The renderer receives a bounded native-serialized payload. It cannot change filters or request refresh: the only inbound messages are bounded readiness/error acknowledgements checked against origin, main frame, session token and render sequence. There is no JavaScript interface or credential bridge. Native Close and source-table controls remain outside WebView. A two-second readiness timeout or renderer failure replaces the chart with its source table; Retry creates a fresh renderer.

## Acceptance evidence

Local unit tests, emulator screenshots and a prior isolated spike provide partial evidence. They do not establish physical low-memory support, complete TalkBack navigation, keyboard focus, minimum-supported Android/WebView compatibility, comprehensive hostile renderer tests, cold-start/performance budgets or peak memory. Track those acceptance gates in IVI-20 and IVI-24 before treating this feature as release-ready. This branch is separate from the current Play candidate; implementing or reviewing it does not publish it.
