# Challenge submission notes

## What is built

TrustVault is a PDF-sharing application with Administrator, Owner, and Viewer roles. Administrators create and deactivate Owner/Viewer accounts and can inspect recent activity. Owners upload, search, edit, share, soft-delete, and restore PDFs. Owners grant named Viewers READ or EDIT access. Viewers can access only documents with a current grant; EDIT also allows metadata changes. Restoring a deleted document does not restore old grants.

Document authorization is enforced by the Spring Boot service on each protected request. A document ID is not permission. Listings start from a visibility-scoped query, and download/edit/share/revoke/delete/restore operations check current account state, role, ownership, or grant as appropriate. No reusable public file URL is issued. Local bytes are kept outside the web root; the Render demo stores bytes in PostgreSQL.

Audit events are recorded for authentication and document/account actions, including many denied requests. The Administrator can view recent entries. PostgreSQL triggers reject ordinary UPDATE, DELETE, and TRUNCATE on audit rows. The trigger and separate-role script passed a disposable PostgreSQL 16 check on 2026-10-22. The live Render database has **not** been given separate runtime and migration roles: it uses the managed database credential for both API and Flyway. Therefore the live demo does not yet prove the challenge's audit-tamper resistance standard. A database administrator remains trusted in the self-managed design.

## What was checked

- After the same-session revocation regression test was added, the user reported that the backend test suite passed. The exact test count/output was not supplied, so no count is claimed here.
- The user ran the PostgreSQL role-hardening helper successfully against an isolated disposable database on 2026-10-22. It does not mean the live Render database was hardened.
- Render reported the service Live, and the public health endpoint returned `UP` on 2026-10-22.
- The user confirmed that public sign-in and the basic Owner-to-Viewer sharing workflow worked. The live Administrator Activity page also showed successful account creation, upload, sharing, metadata edit, and download events. Dedicated synthetic Owner and Viewer accounts were created and shown Active. With the demo Owner, a harmless sample PDF was uploaded, found by title search, edited, downloaded, soft-deleted, and restored successfully. READ was granted to the dedicated Viewer and revoked; the Owner UI confirmed both. After Viewer sign-in, the shared-files list was empty and the sample's direct download endpoint returned `Document not found`.

## Permission model and known limitations

Permissions are per document, assigned by an Owner to a named active Viewer. READ permits viewing/downloading. EDIT includes READ and permits title/category changes. The backend reloads the current grant on each relevant request; revocation therefore denies the next new request. The app does not issue reusable download links.

This is an MVP, not a production security certification. The automated authorization tests are mostly service-level unit tests; the JPQL visibility query and all HTTP/CSRF routes have not been comprehensively exercised against a real database. PDF validation checks extension, declared MIME type, size, and the `%PDF-` header; it does not parse the complete PDF structure. There is no login rate limiting. The free Render database expires after 30 days and has no backups. The live deployment uses one managed database credential for both app traffic and migrations, so audit triggers are not an independent security boundary against the database credential holder.

Folders, external/public links, document versions, automated retention/purge, multi-organization tenancy, and AI access suggestions are deliberately out of scope. No AI feature is included in the product. If access suggestions are added later, a person must approve each grant and the decision must be audited.

## Five-minute demo outline

1. **0:00–0:35 — Roles:** Sign in as Administrator. Show Overview, Accounts, and Activity. Explain that Administrator manages accounts and reviews activity but cannot open files.
2. **0:35–1:15 — Accounts:** Show the dedicated synthetic Owner and Viewer accounts and their roles/status.
3. **1:15–2:05 — Private document:** Sign in as Owner, upload a harmless sample PDF, search for it, edit its metadata, and download it.
4. **2:05–3:15 — Grant and revoke:** Grant the Viewer READ access, verify the Viewer can download, revoke access, and verify a new request is denied.
5. **3:15–4:05 — Delete and restore:** Demonstrate Owner soft-delete and restore; confirm old Viewer grants stay revoked.
6. **4:05–4:40 — Audit:** Show corresponding allowed and denied events without document bytes or storage keys.
7. **4:40–5:00 — Honest close:** Show `THREATS.md` and describe the live database role-separation gap and remaining verification.

## Hand-in checklist

- [x] Three roles and account administration are implemented.
- [x] PDF upload, organization by category, search, metadata edit, download, soft-delete, and restore are implemented.
- [x] Named per-document READ/EDIT grants and revocation are implemented.
- [x] Server-side ownership/current-grant checks and cross-user service tests exist.
- [x] Audit activity view, threat register, API documentation, migrations, local setup, and deployment instructions exist.
- [x] Public GitHub repository and public HTTPS demo are available: https://trustvault-demo-iy81.onrender.com
- [x] Public health endpoint returned `UP`; user confirmed sign-in and basic Owner-to-Viewer sharing workflow.
- [x] Disposable PostgreSQL hardening check passed on 2026-10-22.
- [x] Rerun the backend suite after adding the same-session revocation test (user reported success; exact count/output not supplied).
- [ ] Add real-database and direct HTTP authorization coverage.
- [x] Clearly document that the live Render database still uses one managed app/migration credential and that its audit-tamper protection is unverified.
- [ ] Apply a restricted runtime/migration role boundary to a production-style database and verify audit protections there. This is not configured on the live Render managed database.
- [x] Create dedicated synthetic Owner and Viewer accounts and a harmless sample PDF in the public demo; passwords are not in source control.
- [ ] Provide judge credentials only through an authorized challenge channel, and verify the dedicated Viewer can sign in.
- [x] Verify the Owner can grant and revoke READ access for the dedicated Viewer.
- [x] Verify Viewer sign-in, empty shared-files list after revocation, and direct download denial.
- [ ] Review the complete audit activity on the deployed app.
- [ ] Record and review the five-minute demo video.
- [ ] Correct prompt-history entries with commit references and push the final hand-in materials.
- [ ] Plan for the free Render database's expiration and lack of backups; export demo evidence before expiry.
