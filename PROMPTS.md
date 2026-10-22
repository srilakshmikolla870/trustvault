# AI prompt and review log

These entries summarize actual requests and review work from the development conversation. They record specific issues found and the commits that contain the corresponding fixes. Reusable context appears at the end. This log does not claim that every feature has production-grade verification.

## 2026-10-18 — Authentication and project foundation

**Prompt summary:** Start the TrustVault MVP using React, Spring Boot, PostgreSQL, and the agreed session-based authentication design.

**Output:** Added the initial project, database schema, authentication, CSRF endpoint, account-role model, bootstrap administrator, and current-account refresh.

**Issue found:** Review identified that a revoked grant needed to be grantable again and that authenticated session principals should not retain password hashes.

**Correction and evidence:** The grant schema permits a later grant after revocation; authenticated principals erase credentials and refreshed principals omit password hashes. See [commit 3c9096b](https://github.com/srilakshmikolla870/trustvault/commit/3c9096bbc74efb34c9dcbc6d53a30e5836ea03d7). Initial tests and runtime checks were not yet available at this stage.

## 2026-10-19 — Administrator account controls

**Prompt summary:** Continue building the MVP so an Administrator can manage Owner and Viewer accounts.

**Output:** Added server-restricted account listing, creation, activation/deactivation, and the matching dashboard UI. Passwords are hashed and excluded from account summaries.

**Issue found:** Account creation must not allow a caller to choose Administrator role, and an Administrator must not deactivate their own account.

**Correction and evidence:** Those restrictions are enforced in the service and covered by service tests. See [commit 2f66413](https://github.com/srilakshmikolla870/trustvault/commit/2f66413b9c4db5d72bc268a4a95d895a7775dfe2). The user later reported the backend unit suite passing after these changes.

## 2026-10-19 — Private PDF upload and download

**Prompt summary:** Build the Owner document workflow after the account and authentication foundation.

**Output:** Added PDF upload validation, private storage, Owner listing/download, audit events, and focused tests.

**Issue found:** A first user test run found checked `IOException` compile errors in the tests. Later startup testing found a Hibernate/PostgreSQL checksum type mismatch, and an upload succeeded before the browser showed a form-reset error.

**Correction and evidence:** The test compile issue was corrected; the checksum mapping and React file-input reset were fixed. The user reported 14 backend tests passing after the test correction, and the frontend production build passed after the UI correction. See [document workflow commit d9d26f0](https://github.com/srilakshmikolla870/trustvault/commit/d9d26f096f51ab0221686b4111149b6cfdd3aff2), [test compile correction 110a2d2](https://github.com/srilakshmikolla870/trustvault/commit/110a2d2), and [runtime/form fix 0fe062a](https://github.com/srilakshmikolla870/trustvault/commit/0fe062a97a69ebdb9f14091c91df7e0132713ba2).

## 2026-10-19 — Per-document access control and revocation

**Prompt summary:** Add named Viewer sharing with READ/EDIT permissions and revocation, and fix issues found while the user tests the app.

**Output:** Added current-grant checks for listing, download, and metadata edit; Owner-only grant management; Viewer selection; and sharing controls.

**Issue found:** Review and user test runs found a missing Java import/type inference issue and tests with unnecessary Owner setup. A service-only test was not enough to prove a database query runs correctly against PostgreSQL.

**Correction and evidence:** The compile and test setup issues were corrected. The user reported 16 backend tests passing. Tests cover current-grant allow/deny and several Viewer denial cases, but direct HTTP and PostgreSQL authorization checks remain incomplete. See [commit b02b660](https://github.com/srilakshmikolla870/trustvault/commit/b02b6605467f6bde17e0004b647260f8b5d465dc).

## 2026-10-22 — Search, activity, document lifecycle, and database hardening

**Prompt summary:** Continue implementation toward the challenge requirements, including search, the Administrator activity view, document editing/deletion/recovery, and audit protection.

**Output:** Added search by title/owner/category/date, activity review, metadata edit, soft-delete/restore, database audit-row triggers, and role-hardening scripts. The user reported 26 backend tests passing and separately ran the role-hardening helper successfully against a disposable PostgreSQL 16 container.

**Issue found:** Testing showed that PowerShell treated expected PostgreSQL trigger errors as terminating errors; the helper also needed a Flyway history fixture and compatibility with PowerShell hosts without `ProcessStartInfo.ArgumentList`.

**Correction and evidence:** The verifier now runs in an isolated temporary database, streams hidden temporary passwords to psql, captures expected stderr, and checks runtime privileges plus audit append/update rejection. The user confirmed the helper printed `DATABASE HARDENING CHECK PASSED`. This verifies the disposable database only; it does not harden the live Render database. See [commit 804b048](https://github.com/srilakshmikolla870/trustvault/commit/804b048458250361ecc96c4acab9d40507e73c3d).

## 2026-10-22 — Public Render deployment

**Prompt summary:** Make the repository public and deploy a temporary free demo for the challenge.

**Output:** Added the Render Blueprint and combined Docker build. Render deployed the public site at https://trustvault-demo-iy81.onrender.com. The user entered bootstrap credentials directly in Render; deployment succeeded, the public health endpoint returned `UP`, and the user confirmed sign-in and the basic Owner-to-Viewer sharing flow.

**Issue found:** Render's managed PostgreSQL service does not support the tested self-managed role-hardening setup. The Blueprint uses the same managed credential for API requests and Flyway; judge credentials and reproducible seed data are not yet documented in the repository.

**Correction and evidence:** The deployment documentation now calls out these limitations instead of claiming production role separation. Do not claim the live audit log is protected from the database credential holder. See [commit 59007fb](https://github.com/srilakshmikolla870/trustvault/commit/59007fb9d62af870c297a71631fe125514ae1763).

## 2026-10-22 — Submission accuracy and revocation regression test

**Prompt summary:** Complete the outstanding challenge hand-in tasks and check that the status report matches the deployed project.

**Output:** Updated the submission, README, deployment, API, test, demo, and threat documentation. Added a service regression test that revokes a Viewer grant and attempts a download with that same Viewer identity before storage access.

**Issue found:** Several documents still said that no public deployment existed. They also treated the 26-test result as current despite later changes and overstated or understated what had been verified.

**Correction and evidence:** Documents now distinguish user-confirmed public workflows, the earlier 26-test result, the disposable database check, and live Render limitations. `npm run build` and `git diff --check` passed. The current Maven test run could not be launched in this environment (`Access is denied` from the Maven executable), so the new Java test is not yet verified. Commit link will be added after commit.

## Reusable context

TrustVault uses React, Java/Spring Boot, PostgreSQL, and session authentication. Deny document access by default. The server checks current ownership or a named per-document READ/EDIT grant on protected requests; roles alone do not grant access to files. Keep file bytes private, make revocation effective for the next request, audit allowed and denied sensitive actions, and never log secrets or file bytes. Review actual diffs and test results; distinguish source-level tests from database and live HTTP evidence. Do not claim Render's managed database has separate runtime/migration roles.
