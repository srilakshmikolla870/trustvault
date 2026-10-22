# Deployment and clean-clone guide

## Local demo deployment

1. Install Git, Docker Desktop, and a current Windows Terminal or PowerShell. Clone the repository.
2. Copy `.env.example` to `.env`. Choose a unique database password and a bootstrap Administrator email/password. Keep `.env` private; it is ignored by Git.
3. From the repository root, run `docker compose --profile app up --build -d`.
4. Open `http://localhost:8081`. The first start applies Flyway migrations and creates the bootstrap Administrator if no Administrator exists.
5. Use [demo/README.md](demo/README.md) to create synthetic Owner/Viewer accounts and a sample document. Use the synthetic PDF generator there.
6. Check service state with `docker compose --profile app ps`; inspect startup errors with `docker compose --profile app logs backend`.
7. Stop services with `docker compose --profile app down`. Named database and document-storage volumes persist. Do not run `down --volumes` unless you intend to erase local demo data.

The normal local development mode remains available: run only `docker compose up -d postgres`, start Spring Boot in `backend`, and run Vite in `frontend`.

### Verification completed locally

On 2026-10-22, the Docker Compose API container started successfully and `http://localhost:8081/api/health` returned `UP`. The disposable PostgreSQL 16 hardening helper also completed successfully, verifying the runtime role's required app permissions, audit-event append, and rejection of an audit-row UPDATE even when attempted as the table owner. The helper uses a temporary container with no published port or persistent volume; it does not harden the local Compose database. Browser sign-in and other user workflows, and any production deployment, still need separate verification.

## Production role separation

The local demo Compose profile uses one database login for convenience. Do not use that configuration as a public production deployment. After V1/V2 have been applied, review and run `backend/deployment/production-postgres-roles.psql` once against the production database as a database administrator. It assumes the existing bootstrap login and database are named `trustvault_app` and `trustvault`; edit the script for provider-specific role/database names first. It creates non-superuser `trustvault_migrator` and `trustvault_runtime` logins, transfers schema/table/sequence ownership to the migration role, removes runtime truncate privileges, and removes login/superuser capability from the old bootstrap role. Runtime DML remains necessary for account and sharing features, so the API process itself remains trusted and can write account/grant rows.

The script uses psql's `\password` prompt for the two new credentials. It passed the disposable PostgreSQL verification on 2026-10-22. This tests the script and role boundary; it does not mean it has been applied to a production database. Set these values in the deployment secret store, never in Git:

- `DB_USERNAME=trustvault_runtime` and `DB_PASSWORD` for normal API requests.
- `FLYWAY_URL`, `FLYWAY_USERNAME=trustvault_migrator`, and `FLYWAY_PASSWORD` for schema migrations.
- `SESSION_COOKIE_SECURE=true` when the public endpoint is behind HTTPS.
- `TRUSTVAULT_STORAGE_DIR` pointing to persistent private storage.

If no `FLYWAY_*` variables are supplied, Flyway intentionally falls back to `DB_*` for local development. Restrict migration credentials to deployment/migration operations and do not give them to the running application beyond startup if your hosting platform supports one-shot migration jobs. The role script has not been executed against a production PostgreSQL service; review it and adapt it to the provider before applying it to real data.

## Public hosting checklist

- Choose a host and database with durable backups and private persistent document storage.
- Build and deploy the containers; publish only the frontend/reverse-proxy port. Keep PostgreSQL private.
- Terminate TLS at a trusted reverse proxy or hosting load balancer and set `SESSION_COOKIE_SECURE=true`.
- Apply the separate database roles described above and store all secrets in the host's secret manager.
- Create dedicated synthetic demo accounts and a synthetic sample document. Put the public demo URL and those dedicated credentials in the repository README only after validating the deployed app.
- Verify that an ungranted Viewer cannot list or download a document, and that revoked access fails on the next request.
- Configure and test database and document-volume backups, restore, monitoring, and retention before using any real data.

This repository does not select a cloud provider or contain credentials for one. No public deployment URL is configured by this guide.
