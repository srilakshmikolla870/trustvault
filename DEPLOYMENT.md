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

## Public demo on a Docker server

The Compose production profile is designed for a small Linux virtual server that you control, such as a DigitalOcean Droplet. This keeps PostgreSQL in the existing Docker stack, where the tested role-hardening script can run with the required database administrator. A managed database that does not grant superuser privileges is not compatible with that script as written. DigitalOcean offers Docker-ready Droplets; its current Basic Droplet pricing starts at $4/month, while a 2 GiB / 2 vCPU option is listed at $18/month. Check the live price and choose a size that fits the app before creating anything. [DigitalOcean Docker image](https://docs.digitalocean.com/products/marketplace/catalog/docker/), [Droplet pricing](https://www.digitalocean.com/pricing/droplets).

1. Create a Docker-ready Ubuntu server in your hosting account. Use SSH key access. The provider’s dashboard shows the server’s public IP. Creating a server starts billing.
2. Register or use a domain you control. Add an `A` record for the domain to the server IP. In the provider firewall, allow inbound SSH from your IP and ports 80 and 443 from the internet. Do not expose PostgreSQL port 5432.
3. Connect to the server over SSH, install Git if needed, and clone this repository. Copy `.env.example` to `.env` and set unique database and bootstrap Administrator passwords. Set `SESSION_COOKIE_SECURE=true` and `TRUSTVAULT_DOMAIN` to the domain from step 2. Keep `.env` only on the server.
4. Start the stack with `docker compose --profile app --profile public -f compose.yaml -f compose.public.yaml up --build -d`. Caddy serves the site on ports 80/443 and obtains HTTPS certificates automatically after DNS points to the server.
5. Check `https://your-domain/api/health` returns `UP`, then sign in as the bootstrap Administrator and create synthetic Owner and Viewer accounts.
6. Apply the database role hardening before using the demo publicly. From the repository root, run:

   ```sh
   docker cp backend/deployment/production-postgres-roles.psql "$(docker compose --profile app -f compose.yaml -f compose.public.yaml ps -q postgres)":/tmp/production-postgres-roles.psql
   docker compose --profile app -f compose.yaml -f compose.public.yaml exec postgres psql -U trustvault_app -d trustvault -f /tmp/production-postgres-roles.psql
   ```

   Enter two unique passwords when psql prompts for the runtime and migration roles. Update the server `.env` to use `DB_USERNAME=trustvault_runtime`, `DB_PASSWORD=<runtime password>`, `FLYWAY_USERNAME=trustvault_migrator`, and `FLYWAY_PASSWORD=<migration password>`, then recreate the backend with `docker compose --profile app --profile public -f compose.yaml -f compose.public.yaml up -d --force-recreate backend`. Never publish these values.
7. Upload the included synthetic PDF as Owner, grant the Viewer READ access, and confirm the Viewer can download it. Then revoke the grant and confirm the Viewer is denied. Publish the public URL and dedicated demo account credentials only after these checks pass.

The API stores PDFs in a Docker named volume, which survives container restarts and redeploys on that server. It does not protect against loss of the whole server. Enable provider backups or configure and test a separate backup process before treating the demo as durable.

## Free Render demo

The repository includes `render.yaml` and `Dockerfile.render` for a no-cost, short-lived demonstration. The Docker image builds the React frontend into the Spring Boot application, keeping the UI, API, session cookie, and CSRF requests on the same HTTPS origin; the existing sign-in page code does not need cross-origin changes. Render deploys a free web service and a free PostgreSQL database in Singapore. The web service receives a Render HTTPS address automatically; a custom domain is optional.

1. Make the GitHub repository public and connect it to Render. In Render, create a new Blueprint from `srilakshmikolla870/trustvault` and select the `main` branch.
2. During Blueprint setup, provide a synthetic bootstrap Administrator email and a unique password of 12–72 characters when Render prompts for the two secret variables. Do not put credentials in the repository.
3. Confirm that both resources are set to the free plan before applying the Blueprint. The database is limited to 1 GB, expires 30 days after creation, and has no backups. The web service sleeps after 15 minutes without traffic and loses its local files on restart; the Blueprint therefore stores uploaded PDF bytes in the PostgreSQL `document_contents` table.
4. After the deployment reports Live, open the service's `onrender.com` address and check `/api/health` returns `UP`. Sign in as the bootstrap Administrator, create synthetic Owner and Viewer accounts, then upload `demo/TrustVault-Sample.pdf` as Owner and grant the Viewer READ access.
5. Test the upload/download and share/revoke steps in [TESTING.md](TESTING.md). Expect the first request after idle to take about a minute. Do not upload real personal or confidential documents.

This is a temporary challenge demo, not production hosting. The free PostgreSQL instance will expire after 30 days, the free services can restart, and there are no database backups. The Render managed database does not provide superuser access, so the tested self-managed role-hardening script is not applied by this Blueprint. The app uses the managed database's configured credential for both startup migrations and API requests. Use only synthetic accounts and files, and do not claim production database-role separation or durable backups for this free deployment. Export any required evidence before the database expires.

## Production role separation

The local demo Compose profile uses one database login for convenience. Do not use that configuration as a public production deployment. After V1/V2 have been applied, review and run `backend/deployment/production-postgres-roles.psql` once against the production database as a database administrator. It assumes the existing bootstrap login and database are named `trustvault_app` and `trustvault`; edit the script for provider-specific role/database names first. It creates non-superuser `trustvault_migrator` and `trustvault_runtime` logins, transfers schema/table/sequence ownership to the migration role, removes runtime truncate privileges, and removes login/superuser capability from the old bootstrap role. Runtime DML remains necessary for account and sharing features, so the API process itself remains trusted and can write account/grant rows.

The script uses psql's `\password` prompt for the two new credentials. It passed the disposable PostgreSQL verification on 2026-10-22. This tests the script and role boundary; it does not mean it has been applied to a production database. For the self-managed Docker PostgreSQL server above, copy the script into the database container and run it interactively as `trustvault_app` (which is the initial database administrator in the Compose setup). Then set these values in the server's private `.env`, never in Git:

- `DB_USERNAME=trustvault_runtime` and `DB_PASSWORD` for normal API requests.
- `FLYWAY_URL`, `FLYWAY_USERNAME=trustvault_migrator`, and `FLYWAY_PASSWORD` for schema migrations.
- `SESSION_COOKIE_SECURE=true` when the public endpoint is behind HTTPS.
- `TRUSTVAULT_STORAGE_MODE=filesystem` for local/private server disk storage. The optional Render demo uses `database` mode so PDF bytes are stored in PostgreSQL.

If no `FLYWAY_*` variables are supplied, Flyway intentionally falls back to `DB_*` for local development. Restrict migration credentials to deployment/migration operations and do not give them to the running application beyond startup if your hosting platform supports one-shot migration jobs. The role script has not been executed against a production PostgreSQL service; review it and adapt it to the provider before applying it to real data.

## Public hosting checklist

- Choose a host and database with durable backups and private persistent document storage.
- Build and deploy the containers; publish only the frontend/reverse-proxy port. Keep PostgreSQL private.
- Terminate TLS at a trusted reverse proxy or hosting load balancer and set `SESSION_COOKIE_SECURE=true`.
- Apply the separate database roles described above and store all secrets in the host's secret manager.
- Create dedicated synthetic demo accounts and a synthetic sample document. Put the public demo URL and those dedicated credentials in the repository README only after validating the deployed app.
- Verify that an ungranted Viewer cannot list or download a document, and that revoked access fails on the next request.
- Configure and test database and document-volume backups, restore, monitoring, and retention before using any real data.

No public server, domain, backups, or deployment credentials are configured by this repository. No public deployment URL exists yet.
