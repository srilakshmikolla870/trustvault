# TrustVault

Secure document sharing for the Snovasys Fresher Hiring Challenge 2026.

## Current status

The application is deployed at [https://trustvault-demo-iy81.onrender.com](https://trustvault-demo-iy81.onrender.com). The user confirmed sign-in and the Owner-to-Viewer sharing workflow on the public demo on 2026-10-22; the public health endpoint returned `UP` on the same date.

Owners can upload, list, download, search, edit, share, soft-delete, and restore PDFs. Named Viewers receive READ or EDIT grants, and Owners can revoke access. The server checks current access on protected requests. Administrators manage accounts and review recent activity but do not gain document access from their role.

The audit append-only triggers and separate migration/runtime roles were verified on a disposable PostgreSQL 16 database on 2026-10-22. **They have not been applied to the live Render database.** Render's current Blueprint uses one managed credential for both API requests and Flyway migrations. Credential rotation alone would not prove least-privilege SQL grants, so do not claim live role separation unless the grants are tested. The free database expires after 30 days and has no backups. Use synthetic demo content only.

Dedicated synthetic Owner and Viewer accounts have now been created in the live demo, and a harmless sample PDF has been uploaded for the Owner. With approval, the sample was granted READ access to the Viewer and then revoked; both results appeared in the Owner UI. After signing in as the Viewer, the shared-files list was empty and a direct GET to the revoked sample's download endpoint returned `Document not found`. Their passwords are intentionally not stored in this public repository; provide passwords only through a challenge-approved demo-credentials channel if one is available.

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

- Backend suite: **37 tests passed, 0 failures, 0 errors** on 2026-10-22. It includes direct MockMvc HTTP-boundary checks for cross-owner download, Viewer privilege denials, revoked access, inactive sessions, missing CSRF, admin authorization, safe activity output, and allowed Owner download. These checks use mocked persistence and storage; they are not a real PostgreSQL or live Render integration test.
- PostgreSQL role-hardening helper: the user ran it successfully against a disposable PostgreSQL 16 container on 2026-10-22. This did not change or validate the live Render database.
- Public deployment: Render reported Live; the public `/api/health` endpoint returned `UP` on 2026-10-22.
- Manual browser checks: the user confirmed public sign-in and the basic Owner-to-Viewer sharing workflow. On 2026-10-22, the live Administrator Activity page showed successful account creation, upload, sharing, metadata edit, and download events. In the dedicated demo Owner session, sample upload, title/category search, metadata edit, download, soft-delete, and restore all succeeded. READ grant and revocation for the dedicated Viewer were confirmed in the Owner UI. After Viewer sign-in, the empty shared-files list and direct HTTP download denial (`Document not found`) were verified.
- Frontend production build passed during earlier feature work. GitHub Actions builds the frontend and runs backend tests but does not deploy.

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

See [SUBMISSION.md](SUBMISSION.md) for the challenge write-up, five-minute demo outline, and remaining hand-in checklist. [demo/README.md](demo/README.md) explains how to create synthetic demo accounts and a sample document.

## Authentication foundation

Spring Security verifies BCrypt password hashes and stores authentication in a server session. Roles come from the database. Authenticated requests reload account status and role so disabling an account or changing its role affects the next request. Browser state-changing requests require a CSRF token obtained from `/api/auth/csrf`. Set `SESSION_COOKIE_SECURE=true` behind HTTPS. The bootstrap admin is created only if no administrator exists; there are no hard-coded demo credentials.

## Administrator account management

After signing in as the bootstrap administrator, the React interface can create owner and viewer accounts and activate/deactivate those accounts. Only the server-authorized administrator route can do this. Account creation accepts a one-time password and stores only its BCrypt hash; the account listing never returns password fields. An administrator cannot deactivate their own account. New accounts are for local synthetic testing; do not use real confidential documents or production passwords in this MVP.

## Initial document workflow

Sign in with an OWNER account and submit a PDF with a title and category. Uploads are limited to 10 MiB and validated by file extension, MIME type, and PDF header signature. Bytes are stored outside the web root with a generated opaque name. Local development uses a private filesystem directory; the Render demo stores bytes in PostgreSQL. The owner can list and download their own active documents. The download endpoint checks ownership on every request, uses attachment delivery, and disables caching. Do not place filesystem storage under a publicly served frontend path.

Owners can share a file with an active Viewer using READ or EDIT access from each document row. READ permits listing and downloading. EDIT also permits title/category changes through the document list. Revocation is checked against the database on each new request, so the next request from the same logged-in Viewer is denied. Administrators do not gain document access from their role. Search is available by title, owner, category, and creation date. Owners can soft-delete documents and restore them from the Recently deleted section; a restored file must be shared again to give Viewers access. Audit rows are protected by database triggers against ordinary update, delete, and truncate operations.
