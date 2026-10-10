# Railway backend

Miniscore **1.7.0 / code 13** reads [the Railway API](https://miniscore-api-production.up.railway.app/games). The [owner dashboard](https://miniscore-api-production.up.railway.app/admin) edits the PostgreSQL-backed publication. [Railway project](https://railway.com/project/43e82cd3-ad0e-4e4c-8de1-8e093a36d642).

Stats no longer depend on GitHub JSON in this APK. Older APKs retain their original URL, so the legacy GitHub publisher remains active for those installations.

## Schedule and preservation

The single API worker checks every 30 seconds for its **08:00 Europe/Skopje** daily import, including daylight saving time. The attempt's local date is stored in PostgreSQL. Restarts and overlapping deployments cannot run a second daily attempt. Starting after 08:00 catches up that day. Keep one replica and server sleeping disabled because the scheduler runs inside the API process.

The existing Python ESPN adapter fetches yesterday, today and tomorrow in NBA Eastern time, retains seven days (maximum 100 games), and defaults to the newest completed game. A manually chosen default remains effective while that game is retained.

Provider errors or invalid data preserve the last publication and revision. The dashboard shows failures. There is one scheduled attempt daily; **Import now** permits a manual retry. This is daily updating, not continuous live polling. ESPN public endpoints are an existing integration without a contracted coverage/availability guarantee; sometimes the source supplies whole minutes only.

## Owner dashboard

The browser prompts for HTTP Basic credentials over HTTPS. Railway holds `ADMIN_USERNAME` and `ADMIN_PASSWORD`. The generated login is in local, Git-ignored `.railway-release/admin-access.txt` with permissions 0600. Passwords are not in the APK or source control. Close the browser session to clear remembered credentials.

1. Search for a game and select it.
2. Edit date, tipoff UTC, status, venue, team names/logos, scores, quarters, player names/photos/jerseys, starters and stats.
3. **Save draft** validates and stores a private draft; **Preview** renders scores and player lines locally.
4. **Publish** validates and commits corrections transactionally. Refresh Miniscore to load them.

Corrections follow player IDs rather than roster positions and are applied after imports. **Clear corrections** restores current provider values for a game. **Make default** changes the default game. **Publication history** restores any of the ten previous publications.

Stale editors receive HTTP 409 after the publication revision changes. Stale drafts are identified rather than silently applied. This first editor edits existing games and rosters; the importer adds new provider games. It does not manually create games or roster entries.

Integers and arrays are strict. FGM cannot exceed FGA; minutes/seconds are bounded. Final scores must equal both player points and quarter totals. Correct related values together when changing final points.

## API

| Endpoint | Result |
| --- | --- |
| `GET /games` | Complete schema-v1 publication for Android |
| `GET /games?date=YYYY-MM-DD` | Date-filtered games; empty results have a null default |
| `GET /games/latest` | Default game |
| `GET /games/{id}` | One game, or 404 |
| `GET /health` | Database-backed readiness and revision |

The full publication includes ETag, revision and update-time headers. Admin routes require authentication, disable private-response caching, reject cross-origin writes and require a custom request header. Android stays read-only and validates before atomically replacing its offline cache.

## Development and deployment

Python 3.12:

```sh
python -m venv .venv
.venv/bin/pip install -r backend/requirements-dev.txt
.venv/bin/python -m pytest backend/tests -q
python -m unittest discover -s scripts/tests -v
```

For local development, set `DATABASE_URL=sqlite:///backend/local.db`, `ADMIN_USERNAME` and a random `ADMIN_PASSWORD` of at least 20 characters. Run `uvicorn backend.app:create_app --factory`. SQLite is local-only; production uses PostgreSQL transaction and advisory locks.

The root `Dockerfile` builds the backend/importer. `.dockerignore` excludes Android files, local credentials and artifacts. Railway settings live in `.railway/railway.ts`, preserving server-side credentials:

```sh
npm ci
railway link --project 43e82cd3-ad0e-4e4c-8de1-8e093a36d642
railway config plan
railway config apply
railway up --service miniscore-api --detach
```

Review the plan; keep the database and volume. CLI deployments are currently manual; GitHub autodeploys are not connected. API/database usage consumes the owner's Railway allowance. Publication history is not a database backup.

Android's optional build-time `gameFeedUrl` property selects another HTTPS endpoint. Changing the installed endpoint requires an APK update; updating stats at this endpoint does not.
