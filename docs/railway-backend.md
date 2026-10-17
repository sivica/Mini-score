# Railway backend

Miniscore **1.7.0 / code 13** reads [the Railway API](https://miniscore-api-production.up.railway.app/games). The [owner dashboard](https://miniscore-api-production.up.railway.app/admin) edits the PostgreSQL-backed publication. [Railway project](https://railway.com/project/43e82cd3-ad0e-4e4c-8de1-8e093a36d642).

Stats no longer depend on GitHub JSON in this APK. Older APKs retain their original URL, so the legacy GitHub publisher remains active for those installations.

## Schedule and preservation

The single API worker checks every 30 seconds for its **08:00 Europe/Skopje** daily import, including daylight saving time. The attempt's local date is stored in PostgreSQL. Restarts and overlapping deployments cannot run a second daily attempt. Starting after 08:00 catches up that day. Keep one replica and server sleeping disabled because the scheduler runs inside the API process.

Both the daily import and **Import now** use the shared adapter's finished-only mode. They fetch **today and yesterday in NBA Eastern time (America/New_York)** and publish only completed games in that window, removing scheduled, live, postponed, cancelled, older and future games from the active feed. Existing game IDs are preserved by date and team identity. The newest completed game is the default unless a manually chosen default is still retained. Previously validated finals inside the window are retained if the provider temporarily omits them. Imports keep the 100-game limit and all box-score validation.

If there are no finished games within the window, or the source fails validation, the last validated publication and revision are preserved rather than publishing an empty feed. Corrections, saved drafts, draft versions and publication history remain in PostgreSQL; pruning the active game list does not delete those records. No APK update is required; reopen Miniscore or pull to refresh.

Provider errors or invalid data preserve the last publication and revision. The dashboard shows failures. There is one scheduled attempt daily; **Import now** permits a manual retry. This is daily updating, not continuous live polling. ESPN public endpoints are an existing integration without a contracted coverage/availability guarantee; sometimes the source supplies whole minutes only.

## Owner dashboard

The browser prompts for HTTP Basic credentials over HTTPS. Railway holds `ADMIN_USERNAME` and `ADMIN_PASSWORD`. The generated login is in local, Git-ignored `.railway-release/admin-access.txt` with permissions 0600. Passwords are not in the APK or source control. Close the browser session to clear remembered credentials.

1. Search for a game and select it.
2. Edit date, tipoff UTC, status, venue, team names/logos, scores, quarters, player names/photos/jerseys, starters and stats.
3. **Save draft** validates and stores a private draft; **Preview** renders scores and player lines locally.
4. **Publish** validates and commits corrections transactionally. Refresh Miniscore to load them.

Corrections follow player IDs rather than roster positions and are applied after imports. **Clear corrections** restores current provider values for a game. **Make default** changes the default game. **Publication history** restores any of the ten previous publications.

Stale editors receive HTTP 409 after the publication revision changes or another editor changes the same game's draft. Each game has a monotonically increasing draft version; save and publish requests must supply the version read by that editor. Rejected tabs keep their unsaved fields. Existing drafts receive version 1 on upgrade without changing their contents; tabs opened before this API upgrade must reload before saving. **Review saved draft** compares saved edits with current values; **Load draft edits for review** recovers them without publishing. New drafts retain their original game snapshot, so recovery applies only edited fields and preserves new provider stats, matching players by ID. Older drafts without snapshots display all differences for explicit review. Removed players' edits remain visible in the comparison but cannot be applied. Saving replaces that draft only when its version still matches. Actions that reload the editor warn before discarding unsaved fields.

This first editor edits existing games and rosters; the importer adds new provider games. It does not manually create games or roster entries. Scheduler database reads run in the thread pool so a slow database check does not block the API event loop.

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
npm ci
npx playwright install chromium
node --test backend/tests/admin-browser.cjs
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

Review the plan; keep the database and volume. API/database usage consumes the owner's Railway allowance. Publication history is not a database backup.

### Automatic deployments from main

`.github/workflows/railway-production.yml` deploys after **Test Miniscore backend** succeeds for a push to `main`. PR checks and fork runs cannot deploy. The workflow skips superseded commits, serializes deployments, uploads the tested commit, waits for that exact deployment to become healthy, then calls the existing authenticated import endpoint. A manual run is available on `main`, but it also requires passing backend CI for that commit.

The `railway-production` GitHub environment is restricted to `main`. Its `RAILWAY_TOKEN` is a Railway project token scoped to this project's **production** environment, not an account-wide token. Only the deploy/import steps receive it. The pinned Railway CLI retrieves the existing API owner credentials at runtime for the import; they are not copied into GitHub secrets or printed. The import script rejects redirects, retries only definite import-lock conflicts, and validates the public finished-game feed afterwards. An ambiguous network failure is not retried automatically.

The Railway service's native GitHub auto-deploy source must remain disconnected so one merge cannot produce two deployments. No Mac, browser session, new service, hourly checker or APK update is required. The daily 08:00 Europe/Skopje import continues independently. Main pushes also retain the separate existing Play Internal testing workflow.

Deployments do not replace PostgreSQL or its volume. Imports preserve manual corrections and saved drafts. Failed imports retain the previous validated publication and fail the workflow visibly; a successful backend deployment can therefore coexist with a failed stats-refresh step. If no games have finished in the today/yesterday window, the old feed remains available and the workflow reports that refresh failure. Provider licensing remains a separate unresolved release concern.

To revoke automation, delete the GitHub environment secret and revoke its named project token in Railway. To retry a failure, rerun the GitHub workflow against current, passing `main`; check the job logs and the public `/health` and `/games` responses. Do not print Railway variables or owner credentials when debugging.

## Encrypted daily backups on Hobby

Railway's API rejected volume backups and schedules on this Hobby workspace because those features require Pro. The independent `miniscore-backup` service instead runs daily at **07:15 UTC** (09:15 summer / 08:15 winter in Skopje). It reads the complete `miniscore_state` row over the private network and uploads an AES-256-GCM encrypted, compressed logical backup to the private `miniscore-backups` Railway bucket. This row is the entire application database: provider/public games, manual corrections, drafts, versions, publication history and import metadata. Database roles, passwords and Railway configuration are outside this logical backup and must be provisioned separately.

Each run reads its uploaded archive back, checks exact bytes and authenticated decryption, restores it into an isolated SQLite database and checks exact state equality. Only after this succeeds does it write `miniscore/latest.json` with the archive key, date, revision, SHA-256 and `restoreVerified`. PostgreSQL restoration and transaction locks also have isolated CI tests. Failures exit nonzero; inspect the backup service's Railway logs/deployment status and the latest manifest date. Archives are retained without automatic deletion. The worker and bucket consume ordinary Hobby usage; no plan upgrade was made.

On 2026-10-10, two production backups completed successfully. The post-migration archive `miniscore/2026/10/10/152252609479.aesgcm` was independently downloaded, decrypted and restored; its restored state matched the deployed admin state, including draft versions. Deployment `29da41b0-5ca1-49e7-bed2-4a940f281eef` preserved public response bytes and all pre-existing drafts, corrections and history while adding draft counters. The worker's next scheduled run is 2026-10-11 at 07:15 UTC. Use Railway's **Cron Runs → Run now** to make an immediate backup; deploying a cron image alone does not execute the job.

Only the backup service receives the bucket credentials and `BACKUP_ENCRYPTION_KEY`. Local recovery copies are in ignored `.railway-release/backup-credentials.json` and `.railway-release/backup-encryption-key.txt`, both mode 0600. Preserve an independent secure copy of the encryption key; losing it makes archives unreadable. The API and Android app receive no backup credentials. `.railway/railway.ts` records the bucket, worker, schedule and preserved environment variables.

To recover, provision a fresh PostgreSQL database from the existing Railway configuration and use `BACKUP_RESTORE_DATABASE_URL` for its private URL. Select an archive from the private bucket's manifest, then run the backup image with:

```sh
python -m backend.backup verify --object-key miniscore/YYYY/MM/DD/TIMESTAMP.aesgcm
python -m backend.backup restore --object-key miniscore/YYYY/MM/DD/TIMESTAMP.aesgcm
```

Restoration refuses to overwrite existing Miniscore rows. Verify the recovered API on the isolated database before deliberately switching production's database reference. Restarting the API migrates legacy draft counters and clears a recovered in-progress import flag. Production restoration is a separate owner-authorized operation; the verification job never changes production data.

References: [Railway storage buckets](https://docs.railway.com/storage-buckets), [Railway volume backups](https://docs.railway.com/volumes/backups).

Android's optional build-time `gameFeedUrl` property selects another HTTPS endpoint. Changing the installed endpoint requires an APK update; updating stats at this endpoint does not.
