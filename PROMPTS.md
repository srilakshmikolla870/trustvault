# AI Prompt and Review Log

Record real AI-assisted work during development. Do not backdate entries or invent defects, tests, commits, or results. Replace this note with dated entries as changes are reviewed and committed. At least two entries must concern authorization, access control, or audit integrity.

## Reusable context

TrustVault uses React, Java/Spring Boot, PostgreSQL, and a five-day MVP window. Deny document access by default. The server checks current ownership or named per-document READ/EDIT grant on every operation. Roles alone never grant document access. Store documents privately, make revocation effective for the next request, audit allowed and denied actions, and never log secrets or document bytes. Review the diff and run focused tests; report only actual results.

## Entry template

```text
## YYYY-MM-DD — [task]
Tool/model: [if known]
Prompt or fair summary: [what was actually asked]
Output: [what it actually produced]
Issue found or verification performed: [specific and truthful]
Correction and evidence: [change, real test/command/result]
Commit: [actual hash/link after commit]
```

The initial project foundation was drafted in the Codex session on 2026-10-18. This is not yet a completed scored entry: there is no commit link, and backend/database execution could not be verified in the current environment.

## 2026-10-19 — Admin-only owner/viewer account management

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue TrustVault MVP development from the existing Java/Spring Boot and React project.
**Output:** Added administrator-only APIs and a React panel to list users, create owner/viewer accounts, and activate/deactivate non-admin accounts, with audit writes.
**Issue found or verification performed:** Repository inspection showed there was no way to provision owners or viewers, blocking realistic document-sharing tests. The API route requires the server-side administrator role; the service rejects administrator-role creation and self-deactivation; password hashes are omitted from response records.
**Correction and evidence:** Implementation and focused service tests are present in `backend/src/main/java/com/trustvault/users/UserAdminService.java`, `UserAdminController.java`, `backend/src/test/java/com/trustvault/users/UserAdminServiceTest.java`, and `frontend/src/main.jsx`. `npm run build` passed with Vite 8.3.4 (15 modules transformed). Backend tests are pending; no result is claimed for them.
**Commit:** `2f66413` (local commit; GitHub push is pending because the Git credential helper failed to start in this environment).

## 2026-10-19 — Owner PDF upload and private download workflow

**Tool:** Codex coding assistant
**Prompt or fair summary:** Commit and push the current work, then start developing the next MVP feature.
**Output:** Created a document workflow for owners: upload validated PDFs, list owned files, and download only after a server-side ownership check. Added private local storage, generated opaque keys, document audit events, UI integration, API docs, threat notes, and focused service tests.
**Issue found or verification performed:** Repository inspection showed the document tables existed, but no document endpoints or owner-facing upload/list/download flow existed. Code review found that successful events should share the database transaction while denied events must survive the request rollback; the audit writer now has separate transactional paths for those outcomes. The user's first Maven run found two test methods missing declared `IOException`; the fix was followed by a 14-test pass. A later application startup showed Hibernate expected `VARCHAR` for `checksum_sha256` while PostgreSQL has `CHAR(64)`. The entity mapping was changed to use Hibernate's CHAR JDBC type; rerun is pending. The user's upload test then exposed a frontend error because `event.currentTarget` was read after an async request; the document request may already have succeeded before the reset error appeared.
**Correction and evidence:** `npm run build` passed after the Owner workspace integration and again after replacing the post-submit event access with a file-input ref. The user ran Maven on 2026-10-19: 14 tests passed, 0 failures, 0 errors, and 0 skipped, before the checksum type-mapping correction. The added document tests cover invalid PDF signature rejection, successful upload summary, and cross-owner download denial before storage read. The latest backend startup and refreshed-browser upload flow remain unverified.
**Commit:** `d9d26f0` (document workflow) and `0fe062a` (startup mapping and upload reset fixes; local commits pending push).

## 2026-10-19 — Named Viewer grants and revocation

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue MVP development after confirming the local main branch was clean and synchronized with origin.
**Output:** Added active Viewer selection, per-document READ/EDIT grant and revoke endpoints, current-grant checks on list/download/metadata update, Owner sharing controls, Viewer document list, and focused service tests.
**Issue found or verification performed:** Initial repository state had in-progress grant entity and repository files but no service/controller/UI integration. Frontend production build passed (`npm run build`, Vite 8.3.4; 15 modules). The user's first Maven run found a missing `UserEntity` import and related inferred-list type error; after that fix, the next run compiled Java but found unnecessary shared Owner-account stubbing in two Viewer tests.
**Correction and evidence:** Added the missing import, explicit `List<GrantSummary>` type, and moved Owner test setup into only the tests that use it. The user then ran `mvn test`: 16 tests passed, 0 failures, 0 errors, 0 skipped. These service tests cover current READ grant download and no-current-grant denial; PostgreSQL revocation behavior and HTTP/CSRF checks remain unverified. The initial feature commit is local; GitHub push attempt failed because this session could not resolve GitHub.
**Commit:** `b02b660` (local; push pending).

**Follow-up from screenshot review:** The Owner screenshot showed document names squeezed out by the share controls, and the Share button disabled with no active Viewer selected. Updated the document row to place sharing controls on a second line and added an explicit empty-viewer message. `npm run build` passed after the change. This improves visibility but does not establish whether this user's backend currently has an active Viewer; manual account/API verification is still needed.

**Second screenshot follow-up:** The Administrator screen showed active Viewer accounts, while the Owner screen received a non-success response from `/api/documents/eligible-viewers`. The screenshots do not expose the HTTP status, so a stale backend process is a likely cause, not yet proven. Updated the frontend to show the status and avoid duplicating the error message. `npm run build` passed. Restart the backend and inspect the displayed status if it persists; direct endpoint response is still unverified.

**Administrator navigation:** Based on the user's request, separated Overview summary cards and the Accounts directory into distinct sidebar pages. The URL hash and browser navigation keep the selected page in sync. `npm run build` passed. Commit: `55a5f6b` (local UI change; prompt log update follows).

## 2026-10-19 — Interactive admin overview and return navigation

**Tool:** Codex coding assistant
**Prompt or fair summary:** Make the Viewers summary open the matching account list, clarify the purpose of the organization and access sidebar elements, and add a back arrow to return to Overview.
**Output:** Made each overview summary card open the account directory with a matching role/status filter, added a status filter, clarified the current workspace and access protection text, and added a keyboard-accessible “Back to overview” button.
**Issue found or verification performed:** The organization selector and “Access matters” panel looked interactive but had no action. This project currently exposes one workspace only, so the label is now presented as workspace context; the access panel now explains the implemented Owner/Viewer policy and Administrator restriction.
**Correction and evidence:** `npm run build` passed with Vite 8.3.4 (15 modules transformed); `git diff --check` passed. Manual browser interaction has not been re-verified in this turn.
**Commit:** `22fac47` (local; push result to be recorded after the network attempt).

## 2026-10-19 — Viewer access-denial regression coverage

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue the next TrustVault development step after the user confirmed upload, sharing, and revocation work; harden the unauthorized Viewer access cases discussed for list, download, edit, and sharing.
**Output:** Added a service test confirming Viewer listings use the visibility-scoped repository query, tests that an ungranted Viewer or READ-only Viewer cannot edit metadata, and tests that Viewers cannot list/create/revoke grants for another owner's document. Updated the threat register with the coverage and verification limits.
**Issue found or verification performed:** Existing source enforced the edit and grant-owner checks, but automated tests did not cover most Viewer denial paths. The current environment rejected execution of the Maven launcher with “Access is denied,” so the newly added Java tests have not been compiled or run here.
**Correction and evidence:** Tests assert that denied edits leave metadata unchanged and do not save, denied grant operations do not write grants, and the listing service calls the visibility-scoped query. This unit test does not execute the query against PostgreSQL. Maven test command to run locally: `mvn test` from `backend` (or use the installed Maven executable). Results are pending; no pass is claimed.
**Commit:** Pending; no commit has been created for this work.

## 2026-10-19 — Sign-in recovery when CSRF setup fails

**Tool:** Codex coding assistant
**Prompt or fair summary:** Fix the unavailable Sign in button shown with “Could not initialize secure session.”
**Output:** Kept CSRF mandatory, made the submit button available when the token bootstrap fails so submission retries token acquisition, and added actionable messages for network, HTTP, and missing-token failures.
**Issue found or verification performed:** The button was disabled whenever the CSRF token was empty, leaving no in-page retry. A sign-in request still requires a valid server-issued CSRF token; this change does not bypass that protection.
**Correction and evidence:** `npm run build` passed with Vite 8.3.4 (15 modules transformed); `git diff --check` passed. Backend runtime was not available for browser verification in this environment.
**Commit:** Pending; no commit has been created for this work.

**Follow-up from user screenshot:** The sign-in page displayed HTTP 502 from the Vite API proxy. The user asked to restore the previous sign-in page code, so reverted the temporary retry-button and expanded-error-message changes. The 502 indicates the frontend could not reach the backend; it is not fixed by changing the sign-in page. Backend startup is required to issue the CSRF token.

## 2026-10-19 — Overview team account preview

**Tool:** Codex coding assistant
**Prompt or fair summary:** Use the empty area beneath the administrator overview summary cards to display the accounts in a professional, clear layout.
**Output:** Added a compact Team accounts panel with account name, email, role, and active status, plus a View directory shortcut. The preview uses the existing admin account data and omits the currently signed-in administrator from the owner/viewer team list.
**Issue found or verification performed:** The Overview page had a large empty area after its summary cards, despite the account list being available on a separate page.
**Correction and evidence:** `npm run build` passed with Vite 8.3.4 (15 modules transformed), and `git diff --check` passed. Browser visual verification is not available in this run.
**Commit:** Pending; no commit has been created for this work.

## 2026-10-18 — MVP foundation and authentication slice

**Tool:** Codex coding assistant
**Prompt or fair summary:** Start TrustVault MVP implementation with the agreed Java, Spring Boot, React, PostgreSQL stack; ignore GitHub connection for now and build locally before deployment/testing.
**Output:** Added the project shell, schema, health endpoint, Spring Security session login/logout, CSRF endpoint, database-backed roles, environment-only administrator bootstrap, per-request account refresh, React sign-in form, and authentication audit writes.
**Issue found or verification performed:** Initial grant uniqueness would have prevented a re-grant after revocation, and a `UserDetails` principal could retain the password hash in the session. Changed the grant index and made principals erase or omit password hashes. Static checks only; Maven/Docker/runtime checks are unavailable here.
**Correction and evidence:** Changes are in `backend/src/main/resources/db/migration/V1__initial_schema.sql`, `backend/src/main/java/com/trustvault/users/AuthenticatedUser.java`, and `backend/src/main/java/com/trustvault/security/CurrentAccountFilter.java`. Static JSON/XML, Vite config syntax, and file/schema assertions were checked; Java tests were added but not run.
**Commit:** Pending; no commit has been created.
## 2026-10-22 — Search documents

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue building the next TrustVault feature after reviewing the project status.
**Output:** Added document search by title/filename, owner name/email, category, and inclusive creation-date range for Owner and Viewer workspaces. API filtering starts from the existing visibility-scoped document list; results include safe owner display metadata for search and display. Updated API/README and threat documentation.
**Issue found or verification performed:** The frontend production build and `git diff --check` passed. Backend Maven tests could not run because Windows denied execution of the Maven launcher in this environment. Browser and live API checks remain pending.
**Correction and evidence:** Search remains limited to active owned documents or currently granted documents; administrator file access is unchanged.
**Commit:** Pending; no commit has been created.
## 2026-10-22 — Administrator activity history

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue building the next TrustVault function.
**Output:** Added an Administrator-only, read-only activity page backed by `/api/admin/activity`, showing the latest 100 audit events with actor, action, resource type, result, and time. The response omits event metadata, request IDs, storage keys, and file bytes.
**Issue found or verification performed:** The existing audit table records the events but there was no API or UI for Administrators to review them. Runtime/API verification remains pending because backend Maven execution was denied by Windows in this environment.
**Correction and evidence:** Added server-side role protection using the existing `/api/admin/**` security rule and capped the requested event limit at 100. The frontend production build passed with Vite 8.3.4 (15 modules transformed).
**Commit:** Pending; no commit has been created.
## 2026-10-22 — Edit document details

**Tool:** Codex coding assistant
**Prompt or fair summary:** Build the next TrustVault function.
**Output:** Connected the existing metadata update endpoint to the document list. Owners can edit title and category; Viewers see Edit details only with EDIT access. Updates use session CSRF protection, update the visible list immediately, and preserve server-side ownership/grant checks.
**Issue found or verification performed:** API metadata editing already existed but was not available in the interface. Frontend production build passed with Vite 8.3.4. Backend Maven tests and live API checks were not run in this environment.
**Correction and evidence:** The API response now includes safe owner display metadata for refreshed search/display results. No document bytes or storage keys are returned.
**Commit:** Pending; no commit has been created.
## 2026-10-22 — Soft delete and append-only audit protection

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue the TrustVault MVP toward the project brief and make the next meaningful feature ready for user testing.
**Output:** Added Owner-only document soft deletion, blocking deleted records from active lists and downloads, and writing a `DOCUMENT_DELETE` audit event. Added PostgreSQL triggers to reject ordinary audit-row update, delete, and truncate operations.
**Issue found or verification performed:** The initial schema already had `DELETED` status and `deleted_at`, but no delete endpoint or UI existed. The database login used for local development has elevated ownership, so a database owner/superuser can still disable the triggers.
**Correction and evidence:** Server-side ownership checks remain authoritative; deletion preserves private bytes for operator recovery. The frontend production build passed with Vite 8.3.4 (15 modules transformed). Backend migration and API runtime verification remain pending.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Restore soft-deleted documents

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue building the next TrustVault function toward the MVP milestone.
**Output:** Added an Owner-only deleted-document listing and restore endpoint, plus a Recently deleted section in the Owner workspace. Restoration preserves the private file and does not revive revoked Viewer grants; the Owner must share again. Restore actions are audited.
**Issue found or verification performed:** The soft-delete flow retained document bytes but offered no self-service recovery. Frontend production build and `git diff --check` passed. Backend tests and live API checks remain pending.
**Correction and evidence:** The recovery route checks current active Owner role and document ownership; other roles cannot use it. Restored files have no active grants until re-shared. No hard purge or retention policy was added.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Close remaining local MVP delivery gaps

**Tool:** Codex coding assistant
**Prompt or fair summary:** Build the remaining project requirements after estimating implementation progress against the Snovasys challenge brief.
**Output:** Added authorization-focused delete/restore unit cases, GitHub Actions jobs for backend tests and frontend builds, container definitions for the API and UI with a full local Compose profile, and challenge write-up/demo outline/checklist documentation.
**Issue found or verification performed:** The challenge requires executable evidence, clean setup/deployment instructions, and a demo/write-up, while the repository had only local PostgreSQL Compose and no CI workflow. The backend suite and live container stack have not been run here.
**Correction and evidence:** Compose keeps the database host-bound to localhost and stores uploaded PDFs in a named volume; the backend container runs as a non-root user. The submission checklist explicitly leaves public deployment, role separation, backups, demo credentials, and video pending.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Prepare remaining local submission assets

**Tool:** Codex coding assistant
**Prompt or fair summary:** Build the remaining project requirements from the attached challenge brief.
**Output:** Added separate Flyway migration and API runtime credential settings, a PostgreSQL production role-hardening script, a clean-clone deployment guide, and instructions for creating synthetic demo accounts and uploading a generated sample PDF. Updated threat and submission checklists to distinguish prepared work from external tasks.
**Issue found or verification performed:** Python was not available in the local shell for generating a sample PDF, so the first generator approach could not be run.
**Correction and evidence:** Replaced it with a PowerShell PDF generator and confirmed it produced `demo/TrustVault-Sample.pdf` (609 bytes). The production SQL script has not been executed; review and verify on a disposable PostgreSQL database before using it elsewhere.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Review database role boundary against required workflows

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue checking the attached challenge requirements against the implementation and close remaining local gaps.
**Output:** Reviewed the production PostgreSQL role script and threat register for alignment with account, grant, and audit workflows.
**Issue found or verification performed:** An initial hardening proposal would have revoked writes to `users` and `document_grants`, which would also break Administrator account creation/status changes and Owner sharing/revocation. A shared API database login cannot distinguish approved service writes from direct SQL issued by a compromised API process.
**Correction and evidence:** Kept the runtime DML needed by those features, retained protections against audit-row mutation and table truncation, and documented the API as trusted plus the residual direct-write risk in `THREATS.md` and `DEPLOYMENT.md`. Script execution and disposable-PostgreSQL verification remain pending.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Check current challenge hand-in status

**Tool:** Codex coding assistant
**Prompt or fair summary:** Check whether the remaining challenge hand-in steps have been completed.
**Output:** Ran the current backend unit suite and checked local database, Docker, repository, deployment, and video indicators.
**Issue found or verification performed:** The Maven command file was found but Windows denied launching it from Downloads; Docker Desktop was not running, port 5432 had no listener, GitHub DNS resolution failed, and no local video or deployment URL was found in project materials.
**Correction and evidence:** Started Maven through the installed Java runtime with Mockito's agent loaded at startup. Result: 26 tests passed, 0 failures, 0 errors, 0 skipped. Docker/PostgreSQL and remote hosting/repository visibility could not be verified in this environment. No commit, push, deploy, or visibility change was made.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Prepare isolated database hardening check

**Tool:** Codex coding assistant
**Prompt or fair summary:** Check the database protection step after the user confirmed Docker Desktop was running.
**Output:** Added a PowerShell helper that starts a separate, unexposed PostgreSQL container without a persistent volume, applies both migrations and the production role script, verifies expected permissions and audit-trigger behavior, then removes the container.
**Issue found or verification performed:** Docker works in the user's PowerShell, but this coding session is denied access to the Docker named pipe. The user's runs exposed two argument-binding errors before V1 was applied: first the wrapper required an explicit command parameter, then PowerShell treated the psql `-d` option as a wrapper parameter.
**Correction and evidence:** Changed the wrapper to accept the complete Docker argument list as an explicit array; a local mock confirmed `-d` is passed through as a literal argument. PowerShell syntax parsing and `git diff --check` pass. The user must rerun the helper; no claim is made that database hardening passed.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Reproduce Flyway history in isolated hardening check

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue the database hardening verification after the temporary database reached production role setup.
**Output:** Updated `backend/deployment/verify-postgres-hardening.ps1` to create the Flyway history table and record V1/V2 after applying both migrations directly, matching the documented post-Flyway state expected by `production-postgres-roles.psql`.
**Issue found or verification performed:** The prior user run successfully applied V1 and V2 and created both roles, then stopped because the expected `public.flyway_schema_history` table did not exist. PowerShell parser syntax and `git diff --check` passed after the change; Docker named-pipe access is unavailable in this coding session, so the full script still needs a user rerun.
**Correction and evidence:** The verifier now prepares the Flyway history table and migration rows before role hardening. No claim is made that the database hardening check passed.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Remove interactive Docker TTY requirement from hardening check

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue the disposable database hardening check after Docker exec failed during the role password prompts.
**Output:** Changed the PowerShell verifier to collect the two temporary role passwords using hidden secure prompts and send them to psql via standard input through `docker exec -i`, without `-t` or password command-line arguments.
**Issue found or verification performed:** The user's rerun applied both app migrations and prepared Flyway history, then Docker returned exit code 3 at the `docker exec -it ... psql` password prompt step. PowerShell syntax parsing and `git diff --check` pass for the revised verifier; the full database check remains unverified until rerun in the user's Docker session.
**Correction and evidence:** Removed the need for an interactive Docker TTY while retaining hidden password entry. No claim is made that database hardening passed.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Run hardening as the database administrator in verifier

**Tool:** Codex coding assistant
**Prompt or fair summary:** Continue fixing the disposable PostgreSQL hardening verifier after role setup stopped while disabling the bootstrap account.
**Output:** Changed the temporary database setup to retain PostgreSQL's separate `postgres` administrator, create `trustvault_app` as the temporary bootstrap superuser, apply migrations as that bootstrap role, and execute the hardening script as `postgres`.
**Issue found or verification performed:** The user's last run reached the final bootstrap-role lockdown statement, then PostgreSQL denied it because the verifier ran the hardening script as a role without SUPERUSER. PowerShell syntax parsing and `git diff --check` pass for the corrected verifier. Docker remains inaccessible from this coding session, so the end-to-end check must be rerun by the user.
**Correction and evidence:** The verifier now models the intended privilege transition: a separate database administrator applies the one-time hardening, which disables the bootstrap superuser login. The hardening check has not yet passed.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Treat the expected audit-trigger rejection as a verifier pass

**Tool:** Codex coding assistant
**Prompt or fair summary:** Complete the disposable database hardening check after PostgreSQL rejected the test UPDATE as intended.
**Output:** Updated the verifier to capture the expected PostgreSQL error and confirm that it specifically contains `audit_events is append-only` before reporting success. Updated the manual test notes to reflect the evidence from the user's run.
**Issue found or verification performed:** The user's run reported hardening completion, passed the runtime privilege and audit append checks, and PostgreSQL rejected an UPDATE with `audit_events is append-only`. PowerShell treated that expected native stderr as a terminating error before the verifier printed its final success line. PowerShell syntax and formatting checks are being run after the change; the end-to-end helper should be rerun to confirm clean completion.
**Correction and evidence:** The verifier now checks both the nonzero exit and the expected append-only message, so unrelated SQL failures won't be counted as a successful trigger check. No claim is made that the verifier printed its final pass yet.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Avoid Windows PowerShell native stderr termination

**Tool:** Codex coding assistant
**Prompt or fair summary:** Fix the hardening verifier after the expected append-only trigger rejection still terminated the PowerShell script.
**Output:** Changed the negative UPDATE check to suppress the expected Docker/psql stderr and judge rejection by psql's nonzero exit status. The earlier attempt to capture native stderr with `2>&1` still surfaced as a terminating `NativeCommandError` in Windows PowerShell.
**Issue found or verification performed:** The user's rerun again showed PostgreSQL rejecting the audit UPDATE, but PowerShell terminated before the verifier could print the final status. PowerShell syntax parsing and `git diff --check` pass for the updated script; the Docker integration run remains pending.
**Correction and evidence:** The helper now redirects only the expected negative-test stderr to null and throws only if psql exits successfully, meaning the UPDATE was unexpectedly accepted. Please rerun once to confirm the full helper prints its final pass.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Capture expected trigger error outside PowerShell native pipeline

**Tool:** Codex coding assistant
**Prompt or fair summary:** Fix the same PowerShell termination recurring during the expected append-only trigger rejection.
**Output:** Replaced the negative UPDATE invocation with `System.Diagnostics.Process` using redirected stdout/stderr and individually added Docker arguments. The helper now verifies both psql's nonzero exit and the trigger's expected `audit_events is append-only` message.
**Issue found or verification performed:** The user's rerun still terminated at Docker's stderr despite explicit redirection in the PowerShell native pipeline. Reproduced the exit-code/stderr capture pattern locally with a harmless command (exit 3 and expected stderr), parsed the full helper successfully, and `git diff --check` passes. This does not exercise Docker/PostgreSQL itself.
**Correction and evidence:** PowerShell no longer handles the negative test as a native command, so its expected stderr cannot terminate the verifier before final status. Please rerun; only the user's complete run can confirm the overall database check.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Support PowerShell runtimes without ProcessStartInfo.ArgumentList

**Tool:** Codex coding assistant
**Prompt or fair summary:** Fix the final verifier step after the user's run passed role setup and privilege checks but stopped on a missing .NET property.
**Output:** Changed the expected-failure subprocess to use the broadly supported `ProcessStartInfo.Arguments` string with quoted Docker arguments.
**Issue found or verification performed:** The user's output confirms `production-postgres-roles.psql` completed, the runtime-role privilege check passed, and audit append passed. The helper then failed because `ArgumentList` was null in the user's PowerShell runtime. Parsed the revised script and verified the argument-string subprocess captures expected stderr and exit code 3 using a local harmless child process; `git diff --check` passes. The actual Docker test still needs a rerun.
**Correction and evidence:** Replaced `ArgumentList.Add()` with a quoted argument string compatible with older .NET ProcessStartInfo implementations. No database pass is claimed until the complete verifier prints it.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Confirm disposable database hardening and local API health

**Tool:** Codex coding assistant
**Prompt or fair summary:** After the verifier passed, check what remains before manually testing the application.
**Output:** Updated the project security and setup documentation to reflect the user's completed verification output.
**Issue found or verification performed:** The complete disposable PostgreSQL 16 run printed `DATABASE HARDENING CHECK PASSED`. It confirmed the runtime role's required application privileges, that it can append an audit event, and that PostgreSQL rejects audit-row UPDATE even when attempted as the table owner. The local Docker Compose API also started, and `http://localhost:8081/api/health` returned `UP` for `trustvault-api`. The backend unit-suite result recorded for this project is 26 passed, 0 failures, 0 errors, 0 skipped.
**Correction and evidence:** Updated `THREATS.md`, `API.md`, `README.md`, and `DEPLOYMENT.md` to distinguish these completed checks from still-pending browser sign-in, other API/document workflows, production database hardening, and public deployment. The disposable helper did not alter the persistent local application database. No sign-in implementation was changed.
**Commit:** Pending; no commit has been created.

## 2026-10-22 — Prepare a public HTTPS Docker deployment

**Tool:** Codex coding assistant
**Prompt or fair summary:** Deploy the project from the GitHub repository to a public hosting service.
**Output:** Reviewed the current Docker Compose setup and hosting requirements. Added a public Compose overlay with Caddy HTTPS proxy configuration and updated deployment documentation for a self-managed Docker server.
**Issue found or verification performed:** The frontend and API currently depend on Compose service DNS, and PDFs are stored on a Docker volume. A managed PostgreSQL option such as Render does not grant superuser privileges, while the existing production role-hardening script requires a database administrator with those privileges. A Render deployment using that script as-is would leave out the database protection documented as required.
**Correction and evidence:** Prepared a Docker-server deployment path using the existing PostgreSQL container, private database port, persistent file/database volumes, and Caddy for HTTPS. No cloud server or paid resource was created, no credentials were added, and no public URL has been deployed. The user must create a server/domain and configure its DNS and firewall before deployment can proceed.
**Commit:** Pending; no commit has been created.
