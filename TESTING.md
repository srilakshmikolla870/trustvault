# TrustVault MVP manual test walkthrough

Use synthetic names, email addresses, passwords, and PDF files. Do not upload confidential or real customer documents.

## Start locally

Follow [README.md](README.md) to configure `.env`, start PostgreSQL, launch the Spring Boot API, and start the React app. Keep the API and frontend terminals open. Sign in with the bootstrap Administrator credentials configured locally; do not place credentials in this file.

## Test account administration

- Create one Owner and two Viewers with separate test email addresses and passwords.
- Confirm each account appears with the expected role and Active status.
- Deactivate one Viewer, confirm the status changes, then reactivate the account.
- Sign out and sign in as the Owner and each Viewer to confirm the roles work.

## Test the Owner document workflow

- As the Owner, upload a small valid PDF with a title and category.
- Confirm it appears in the list and can be downloaded.
- Search by title, filename, category, owner, and creation date.
- Edit the title/category and confirm the list and search results update.
- Share the document with Viewer A using READ. Confirm it appears in A's list and can be downloaded.
- Confirm Viewer A cannot see the Owner's edit, share, or delete controls for a READ grant.
- Change A's permission to EDIT. Confirm A can edit metadata.
- Revoke A's access. While still signed in as A, refresh and confirm the document disappears and its download URL returns not found.
- Share with Viewer B using READ, then soft-delete the document as the Owner. Confirm it disappears from active lists and B can no longer download it.
- In the Owner workspace, confirm it appears under **Recently deleted**. Restore it and confirm it returns to the active list.
- In the Administrator Activity page, confirm both deletion and restoration events appear.
- Sign in as Viewer B and confirm the restored document is still absent and the old download URL is denied. Share it with B again as Owner and confirm it becomes available only after the new share.
- Sign in as a different Owner or Viewer and confirm `/api/documents/deleted` does not reveal the first Owner's deleted files.

## Test activity history

- Sign in as the Administrator and open **Activity**.
- Confirm sign-in, upload, download, edit, share, revoke, and delete events are listed with an actor, result, and time.
- Confirm the page shows no file contents, storage key, or request metadata.
- Try opening `/api/admin/activity` while signed in as an Owner or Viewer; the request must be denied.

## Automated HTTP security checks

Run `mvn test` from `backend`. `DocumentSecurityApiTest` exercises the Spring Security filter chain and document/admin controllers over MockMvc, with mocked persistence and storage dependencies. It covers cross-owner download denial, READ-only edit denial, Viewer grant/revoke denial, revoked-access denial before storage access, inactive-session rejection, missing-CSRF rejection, denied Viewer access to administrator activity, sanitized activity response fields, and an allowed Owner download. These are HTTP-boundary checks, not a live PostgreSQL or deployed Render integration test. `AuditEventWriterTest` checks that the new inactive-session and CSRF denial records use insert-only, minimal event shapes.

## Test database audit protection

- Apply V2 and configure the dedicated `trustvault_runtime` role using [DEPLOYMENT.md](DEPLOYMENT.md).
- Connect as the runtime role and confirm it can read and append audit events through application actions.
- Confirm direct UPDATE/DELETE/TRUNCATE attempts on `audit_events` fail. The runtime role should not own the table or have those privileges; a database superuser or migration owner remains privileged and must be tightly controlled.

For an isolated disposable check, run `./backend/deployment/verify-postgres-hardening.ps1` from the repository root in PowerShell. It starts a PostgreSQL 16 container with no published ports and no persistent volume, applies V1/V2, runs the role-hardening script, checks runtime privileges and the audit trigger, then stops and removes the temporary container. It asks for two temporary role passwords using hidden PowerShell prompts, passes them through psql standard input, and does not connect to the project's local database. Run this from a PowerShell session that can access Docker Desktop.

## Current verification limits

On 2026-10-22, the complete backend suite passed locally: **37 tests, 0 failures, 0 errors**. The 9 new MockMvc security cases and one audit-payload test passed. The frontend production build passed during earlier feature work. The user also reran the disposable PostgreSQL verifier successfully: runtime privilege checks and audit append passed, and the append-only UPDATE trigger rejected the attempted mutation. The public Render health endpoint had returned `UP`, and the user confirmed sign-in and the basic Owner-to-Viewer sharing workflow. Dedicated synthetic demo Owner/Viewer accounts are active. In the demo Owner session, sample upload, title search, metadata edit, download, soft-delete, and restore succeeded. The Owner UI confirmed granting READ to the dedicated Viewer and revoking it. After Viewer sign-in, the empty shared-files list and direct GET denial (`Document not found`) were verified. Search coverage beyond title and a full deployed audit review still need explicit verification. The role-hardening helper has not been applied to the live Render database. Soft-deleted file bytes remain in private storage; there is no automated purge or retention policy.
