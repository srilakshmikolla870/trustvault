# TrustVault

Secure document sharing for the Snovasys Fresher Hiring Challenge 2026.

## Current status

This repository contains the MVP foundation, authentication, account administration, and the first document slice. Owners can upload, list, and download their own validated PDFs from private local storage. Viewer grants, metadata editing, search, soft deletion, document activity views, database-level audit immutability, and deployment are not implemented yet.

## Stack

- Java 17+ and Spring Boot 4.1.1
- Spring Security session authentication and CSRF protection
- PostgreSQL with Flyway migrations
- React and Vite
- Docker Compose for local PostgreSQL
- Private local filesystem storage for development (configured with `TRUSTVAULT_STORAGE_DIR`)

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

## Current verification

- On 2026-10-18, the user ran the backend unit suite: 6 tests passed, 0 failures, 0 errors. These were the authentication/current-account tests before user administration tests were added.
- On 2026-10-19, the user's local log showed PostgreSQL 16.15 connected, Flyway migration V1 validated, Hibernate initialized, and Tomcat started. A later startup attempt failed during bootstrap-admin validation; the current successful browser health/login check is not yet recorded.
- `npm run build` passed after the account-administration UI changes (Vite transformed 15 modules and emitted the production bundle).
- The account-administration backend tests have not been run. The earlier six backend tests passed before this feature was added; do not treat the new API controls as runtime-verified until the updated suite and HTTP checks pass.

Run backend tests from `backend` with `mvn test`. Run the frontend production build from `frontend` with `npm run build`. The new document tests have not yet been successfully run in this environment.

## Security invariants

- Deny document access by default; the backend is the authority.
- Roles alone do not grant document access.
- Check current ownership or per-document grant on every protected operation.
- Store document bytes privately; never expose reusable public URLs.
- Revocation blocks the next new request after it commits.
- Audit successful and denied security-sensitive actions without recording secrets or document bytes.

See [THREATS.md](THREATS.md) for initial threats and verification status.

## Authentication foundation

Spring Security verifies BCrypt password hashes and stores authentication in a server session. Roles come from the database. Authenticated requests reload account status and role so disabling an account or changing its role affects the next request. Browser state-changing requests require a CSRF token obtained from `/api/auth/csrf`. Set `SESSION_COOKIE_SECURE=true` behind HTTPS. The bootstrap admin is created only if no administrator exists; there are no hard-coded demo credentials.

## Administrator account management

After signing in as the bootstrap administrator, the React interface can create owner and viewer accounts and activate/deactivate those accounts. Only the server-authorized administrator route can do this. Account creation accepts a one-time password and stores only its BCrypt hash; the account listing never returns password fields. An administrator cannot deactivate their own account. New accounts are for local synthetic testing; do not use real confidential documents or production passwords in this MVP.

## Initial document workflow

Sign in with an OWNER account and submit a PDF with a title and category. Uploads are limited to 10 MiB and validated by file extension, MIME type, and PDF header signature. Bytes are stored outside the web root with a generated opaque name. The owner can list and download their own active documents. The download endpoint checks ownership on every request, uses attachment delivery, and disables caching. Set `TRUSTVAULT_STORAGE_DIR` to a private persistent directory before running outside local development. Do not place this directory under a publicly served frontend path.

This initial slice does not yet implement sharing grants, Viewer access, editing metadata, search, or soft deletion. A Viewer currently sees an empty document list and cannot download documents.
