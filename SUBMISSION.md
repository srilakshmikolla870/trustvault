# Challenge submission notes

## Short write-up

TrustVault is a private PDF-sharing application. Administrators create and disable Owner and Viewer accounts. Owners upload documents, search and edit metadata, grant named Viewers READ or EDIT access, revoke grants, and soft-delete or restore their documents. Viewers see only documents with a current grant. An EDIT grant also permits metadata updates. Administrators can inspect recent audit activity but do not receive document access from their role.

The authorization decision lives in the Spring Boot service for each document operation. A document ID is never treated as authorization. Listing starts from a database query restricted to the signed-in Owner or a Viewer with a current unrevoked, unexpired grant. Download and edit repeat the current ownership/grant check on the server. Deactivation is refreshed from the database per request. Revocation blocks the next request after the change commits. Documents are stored outside the web root under generated storage keys; the API returns file bytes only after authorization.

The database audit log records authentication, document access and changes, grants, revocations, deletion, restoration, and denied attempts. Migration V2 adds triggers that reject ordinary update, delete, and truncate operations on audit rows. This is not protection against a database superuser or table owner: a public deployment must use a separate migration owner and a restricted runtime database role. Audit rows do not contain document bytes or storage keys.

AI was used for implementation and review support. The prompt history is in `PROMPTS.md`; it records actual issues encountered, including a missing Java import, a frontend async form-reset error, and a stale-backend diagnosis that was initially only a hypothesis. Authorization changes were checked against service-level tests and repository scoping; backend tests for the latest delete/restore changes remain to be run. No AI feature is included in the product. A future access suggestion must never grant access automatically and would need explicit Owner approval and audit records.

This MVP deliberately excludes external/public links, folders, document version history, automated retention/purge, multi-organization tenancy, and AI access suggestions. A free Render Blueprint is included for a temporary HTTPS demo, with PDF bytes stored in PostgreSQL so uploads survive web-service restarts. Render's free database expires after 30 days and has no backups; the demo uses the managed database credential for both migrations and runtime requests, so the self-managed database-role hardening has not been applied. Keep demo data synthetic. A production-grade hosting setup with role separation and backups, public demo accounts, and a recorded demo remains unfinished until the live deployment is verified.

## Five-minute demo outline

1. **0:00–0:35 — Sign in and roles:** Sign in as Administrator. Show the Overview, Accounts, and Activity pages. Explain that Administrator manages accounts and can audit activity, but cannot open files.
2. **0:35–1:15 — Provision test users:** Create one Owner and one Viewer with synthetic credentials. Show role and Active status in Accounts.
3. **1:15–2:05 — Private upload:** Sign in as Owner, upload a small synthetic PDF, search by title/category, edit its metadata, then download it.
4. **2:05–3:15 — Named access and revocation:** Share with the Viewer as READ. Show the Viewer can list/download it but cannot edit or share. Revoke access and show the document disappears or its download request is denied. Emphasize that the server checks the current grant on every request.
5. **3:15–4:05 — Delete and restore:** Share again, soft-delete as Owner, and show the Viewer loses access. Restore from Recently deleted and show the old grant remains revoked until the Owner shares again.
6. **4:05–4:40 — Audit evidence:** Return to Administrator Activity and show allowed and denied events without document bytes or storage keys.
7. **4:40–5:00 — Close honestly:** Show the threat register and describe the known deployment hardening and verification gaps listed above.

## Hand-in checklist

- [x] Authentication with Administrator, Owner, and Viewer roles.
- [x] Upload, organize, search, and manage PDFs.
- [x] Named per-document READ/EDIT grants and revocation.
- [x] Server-side ownership/current-grant checks in document services.
- [x] Audit activity view and threat register.
- [x] API/setup documentation and local Docker Compose profile.
- [x] Synthetic sample PDF generator and safe demo-account setup instructions.
- [x] Optional separate migration/runtime database roles and role-hardening script.
- [ ] Dated AI prompt log with identified issues and correction evidence; entries exist, but uncommitted work does not yet have a corrective commit link.
- [x] Run the current backend unit test suite: 26 passed, 0 failures, 0 errors, 0 skipped (2026-10-22).
- [ ] Run `backend/deployment/verify-postgres-hardening.ps1` from PowerShell with Docker Desktop access; it applies Flyway migrations and checks the production role script on a disposable, isolated PostgreSQL container.
- [ ] Complete GitHub's identity verification to change repository visibility to public.
- [ ] Deploy the free Render Blueprint, verify the public HTTPS app and health endpoint, and create synthetic demo accounts/data.
- [ ] Apply and verify role hardening on a disposable PostgreSQL database for a production-style deployment; the Render-managed free database does not support the current hardening script.
- [ ] Configure production-grade persistent backups; free Render Postgres has no backups and expires after 30 days.
- [ ] Record and review the five-minute demo video.
- [ ] Review and commit the final write-up, test evidence, and submission materials.
