import { bucket, defineRailway, postgres, preserve, project, service, volume } from "railway/iac";

export default defineRailway(() => {
  const Postgres = postgres("Postgres", { region: "sfo" });
  Postgres.networking = { privateNetworkEndpoint: "postgres" };
  const postgresVolume = volume("postgres-volume", { alerts: { usage: { "100": {}, "80": {}, "95": {} } }, allowOnlineResize: true, region: "sfo", sizeMB: 5000 });
  const miniscoreApi = service("miniscore-api", {
    build: { builder: "DOCKERFILE", dockerfilePath: "Dockerfile" },
    healthcheck: "/health",
    healthcheckTimeout: 120,
    deploy: { restartPolicyType: "ON_FAILURE", restartPolicyMaxRetries: 5, sleepApplication: false },
    replicas: { "sfo": 1 },
    env: { ADMIN_PASSWORD: preserve(), ADMIN_USERNAME: preserve(), DATABASE_URL: preserve() },
  });
  const backups = bucket("miniscore-backups", { region: "sjc" });
  const backupWorker = service("miniscore-backup", {
    build: { builder: "DOCKERFILE", dockerfilePath: "Dockerfile.backup" },
    deploy: { cronSchedule: "15 7 * * *", restartPolicyType: "NEVER" },
    replicas: { "sfo": 1 },
    env: {
      DATABASE_URL: preserve(), BACKUP_ENCRYPTION_KEY: preserve(),
      BACKUP_S3_ENDPOINT: preserve(), BACKUP_S3_REGION: preserve(), BACKUP_S3_BUCKET: preserve(),
      BACKUP_S3_ACCESS_KEY_ID: preserve(), BACKUP_S3_SECRET_ACCESS_KEY: preserve(),
    },
  });

  return project("miniscore", {
    resources: [Postgres, miniscoreApi, postgresVolume, backups, backupWorker],
  });
});
