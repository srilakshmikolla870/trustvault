# TrustVault

Secure document sharing for the Snovasys Fresher Hiring Challenge 2026.

## Current status

This repository contains the MVP foundation, authentication, account administration, and document sharing. Owners can upload, list, download, and share validated PDFs with named Viewers as READ or EDIT. Current grants are checked for every list and download request, and Owners can revoke access. Document search by title, owner, category, and date range is implemented. Owners can soft-delete and restore their documents; restoration does not re-enable prior Viewer access. Audit rows reject ordinary updates/deletes/truncation. Separate Flyway/runtime database credentials and a one-time production role-hardening script are provided. The script passed the disposable PostgreSQL verification on 2026-10-22; it has not been applied to a production database. Administrators can review the latest recorded activity.

## Stack

- Java 17+ and Spring Boot 4.1.1
- Spring Security session authentication and CSRF protection
- PostgreSQL with Flyway migrations
- React and Vite
- Docker Compose for local PostgreSQL or the complete local demo stack
- Private filesystem storage locally; optional private PostgreSQL content storage for a free hosted demo

## Prerequisites

- JDK 17 or newer
- Maven 3.6.3 or newer
- Node.js 20.19+ or 22.12+
- Docker Desktop (or a local PostgreSQL 16 instance)

## Local setup

1. Copy `.env.example` to `.env` and set local database values. Never commit `.env`.
2. Set the database and bootstrap administrator environment variables from `.env.example` in your shell or IDE. `.env` is read by Docker Compose; Spring Boot does not load it automatically. Use a unique password at least 12 characters long and no more than 72 UTF-8 bytes; do not commit it.
3. Start PostgreSQL with `docker compose up -d postgres`.
4. In `backend`, run `mvn spring-boot:run`.
5. In `frontend`, run `npm install` and `npm run dev`.
6. Visit the Vite URL printed in the terminal. The API health endpoint is `http://localhost:8080/api/health`.

### Run the complete app with Docker

The default Compose setup starts only PostgreSQL so the API and React development server can run from their folders. To build and run the containerized API and UI as well, set `POSTGRES_PASSWORD`, `TRUSTVAULT_BOOTSTRAP_ADMIN_EMAIL`, and `TRUSTVAULT_BOOTSTRAP_ADMIN_PASSWORD` in `.env`, then run `docker compose --profile app up --build -d`. Open `http://localhost:8081`. The API is behind the UI's same-origin reverse proxy, PostgreSQL is bound to localhost, and uploaded files use a private local directory on a persistent named volume. Use only synthetic demo accounts and documents.

For a free Render demo, use the `render.yaml` Blueprint. It serves the React UI and Spring Boot API from one HTTPS site so the existing same-origin sign-in flow is unchanged. This demo stores PDF bytes in PostgreSQL because free Render web services have temporary filesystems. Render's free PostgreSQL expires after 30 days and has no backups; the free web service may sleep and lose active sessions. Keep the demo synthetic and export anything you need before the database expires. For a longer-lived paid Docker server, follow [DEPLOYMENT.md](DEPLOYMENT.md).

### Continuous integration

GitHub Actions runs the backend unit suite and frontend production build on pushes and pull requests to `main`. The workflow is in `.github/workflows/ci.yml`. It does not deploy the app.

## Current verification

- On 2026-10-18, the user ran the backend unit suite: 6 tests passed, 0 failures, 0 errors. These were the authentication/current-account tests before user administration tests were added.
- On 2026-10-22, the user's local Docker Compose stack started and `http://localhost:8081/api/health` returned `UP` for `trustvault-api`. This confirms the local API health route through the frontend proxy only; browser sign-in and document/account workflows have not been confirmed in that run.
- `npm run build` passed after the account-administration UI changes (Vite transformed 15 modules and emitted the production bundle).
- On 2026-10-22, the backend unit suite passed: 26 tests, 0 failures, 0 errors, and 0 skipped. These are automated tests, not a substitute for checking the UI workflows against the running app.
- The frontend production build passed after the Owner PDF upload/list/download UI was added. Manual HTTP checks for document endpoints and database-backed application integration tests remain pending; only `/api/health` has been manually checked through the local Compose proxy so far.
- A later user test exposed a frontend form reset error after upload; the file input now clears through a React ref. `npm run build` passed after that fix. Confirm the form behavior in the refreshed browser after the backend starts successfully.
- On 2026-10-22, the role-hardening helper completed against a disposable PostgreSQL 16 container. It verified runtime privileges, audit append, and rejection of audit-row UPDATE even for the table owner. This does not change or validate the persistent local or production database configuration.

Run backend tests from `backend` with `mvn test`. Run the frontend production build from `frontend` with `npm run build`.

See [TESTING.md](TESTING.md) for a role-by-role manual walkthrough of upload, search, edit, sharing, revocation, deletion, and activity history.

## Security invariants

- Deny document access by default; the backend is the authority.
- Roles alone do not grant document access.
- Check current ownership or per-document grant on every protected operation.
- Store document bytes privately; never expose reusable public URLs.
- Revocation blocks the next new request after it commits.
- Audit successful and denied security-sensitive actions without recording secrets or document bytes.

See [THREATS.md](THREATS.md) for initial threats and verification status.

See [SUBMISSION.md](SUBMISSION.md) for the challenge write-up draft, five-minute demo outline, and remaining hand-in checklist. [demo/README.md](demo/README.md) explains how to create synthetic demo accounts and seed a sample document.

## Authentication foundation

Spring Security verifies BCrypt password hashes and stores authentication in a server session. Roles come from the database. Authenticated requests reload account status and role so disabling an account or changing its role affects the next request. Browser state-changing requests require a CSRF token obtained from `/api/auth/csrf`. Set `SESSION_COOKIE_SECURE=true` behind HTTPS. The bootstrap admin is created only if no administrator exists; there are no hard-coded demo credentials.

## Administrator account management

After signing in as the bootstrap administrator, the React interface can create owner and viewer accounts and activate/deactivate those accounts. Only the server-authorized administrator route can do this. Account creation accepts a one-time password and stores only its BCrypt hash; the account listing never returns password fields. An administrator cannot deactivate their own account. New accounts are for local synthetic testing; do not use real confidential documents or production passwords in this MVP.

## Initial document workflow

Sign in with an OWNER account and submit a PDF with a title and category. Uploads are limited to 10 MiB and validated by file extension, MIME type, and PDF header signature. Bytes are stored outside the web root with a generated opaque name. Local development uses a private filesystem directory; the Render demo stores bytes in PostgreSQL. The owner can list and download their own active documents. The download endpoint checks ownership on every request, uses attachment delivery, and disables caching. Do not place filesystem storage under a publicly served frontend path.

Owners can share a file with an active Viewer using READ or EDIT access from each document row. READ permits listing and downloading. EDIT also permits title/category changes through the document list. Revocation is checked against the database on each new request, so the next request from the same logged-in Viewer is denied. Administrators do not gain document access from their role. Search is available by title, owner, category, and creation date. Owners can soft-delete documents and restore them from the Recently deleted section; a restored file must be shared again to give Viewers access. Audit rows are protected by database triggers against ordinary update, delete, and truncate operations.
