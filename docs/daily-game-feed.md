# Daily Miniscore updates

The **Update daily NBA game feed** GitHub Actions workflow runs once daily at **08:00 Europe/Skopje**, following local daylight saving time. It runs on GitHub; the developer's computer does not need to stay on. GitHub scheduled runs may be delayed during busy periods.

The updater reads ESPN's public NBA scoreboard and box-score JSON, converts it to Miniscore schema version 1, and publishes only `games.json` on the `game-data` branch. It requires no additional API credentials, Play credentials, or app release. ESPN's public endpoints are not a contracted API and may change or become unavailable; failures stop publication and preserve the previous feed.

At each run it fetches **yesterday and today in America/New_York** and publishes only **FINISHED** games whose NBA Eastern game date is in that window, up to the app's 100-game limit. Scheduled, live, postponed, cancelled, older and future games are excluded, including entries already present in the published JSON. The newest completed game is the default. Existing game IDs are preserved by matching the game date and away/home team codes, so earlier manually added games are not duplicated. Games after midnight UTC still use their NBA Eastern date.

The workflow passes `--finished-only` to the shared importer. Railway's daily and admin imports use the same filter. If there are no completed games in the two-day window, publication fails safely and keeps the previous JSON rather than publishing an empty feed the installed app would reject. Previously validated finals within the window are retained if the provider temporarily omits them.

Player photos and team logos use ESPN image URLs. Player stats include PTS, REB, AST, FGM, FGA and the app's detail stats. Players marked DNP are omitted. ESPN sometimes supplies whole, rounded minutes rather than minute/second precision; in that case seconds are zero. No previous game's stats are copied into a new game. Scheduled/postponed/cancelled games have zero scores and empty player/quarter arrays, and the app displays its scheduled-game state.

The updater validates strict JSON integers, arrays, IDs, dates, colors, image URLs, starters, field-goal counts and size limits. Final scores must match the sums of both player points and quarter scores. A missing box score, incomplete or malformed response, status/score mismatch, timeout or invalid output causes the workflow to fail before writing the file. Atomic replacement and a normal Git push protect the existing publication; a concurrent manual edit causes a push conflict instead of overwriting that edit.

## Run or inspect the update

Open [the workflow](https://github.com/sivica/Mini-score/actions/workflows/daily-game-feed.yml). **Run workflow** can optionally supply an NBA Eastern date (`YYYY-MM-DD`) or enable **dry_run** to validate without publishing. The default uses the current NBA Eastern date and publishes validated changes. Unchanged data creates no commit. Failed runs remain visible in Actions; GitHub notifications depend on the account's notification settings.

The installed app downloads the new JSON when opened/resumed or refreshed. This daily job does not add continuous live polling to the app.

Local verification:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/update_game_feed.py --feed /path/to/copied/games.json --date 2026-10-10 --finished-only
```

Sources: [ESPN scoreboard](https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard), [example ESPN box score](https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event=401898395), [GitHub scheduling and timezones](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onschedule). In public repositories, GitHub disables schedules after 60 days without repository activity; re-enable the workflow if that occurs during an extended offseason without data changes.
