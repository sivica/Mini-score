# Miniscore Railway configuration

The linked project contains `miniscore-api`, PostgreSQL and its persistent volume.
`.railway/railway.ts` preserves existing server-side secrets and configures the
Docker build, `/health` readiness check and restart policy. Keep the API running
with one replica so its daily 8 AM Europe/Skopje scheduler can execute.

Run `npm ci`, `railway config plan`, and inspect the changes before using
`railway config apply`. Keep the database and volume in the configuration.
Deploy source with `railway up --service miniscore-api --detach`.

See [backend documentation](../docs/railway-backend.md) for access, scheduling,
validation, tests and Android integration. The account link is managed by the
Railway CLI outside this repository; admin credentials are never stored here.
