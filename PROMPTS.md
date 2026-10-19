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
**Issue found or verification performed:** Repository inspection showed the document tables existed, but no document endpoints or owner-facing upload/list/download flow existed. Code review found that successful events should share the database transaction while denied events must survive the request rollback; the audit writer now has separate transactional paths for those outcomes. The user's first Maven run found two test methods missing declared `IOException`; both now declare `throws Exception`. The corrected run passed all 14 tests. Git push was attempted but failed because the Git credential helper could not start, so the commits remain local.
**Correction and evidence:** `npm run build` passed after the Owner workspace integration (Vite 8.3.4; 15 modules transformed). The user ran Maven on 2026-10-19: 14 tests passed, 0 failures, 0 errors, and 0 skipped. The added document tests cover invalid PDF signature rejection, successful upload summary, and cross-owner download denial before storage read.
**Commit:** `d9d26f0` (local; corrected backend tests still need a rerun).

## 2026-10-18 — MVP foundation and authentication slice

**Tool:** Codex coding assistant
**Prompt or fair summary:** Start TrustVault MVP implementation with the agreed Java, Spring Boot, React, PostgreSQL stack; ignore GitHub connection for now and build locally before deployment/testing.
**Output:** Added the project shell, schema, health endpoint, Spring Security session login/logout, CSRF endpoint, database-backed roles, environment-only administrator bootstrap, per-request account refresh, React sign-in form, and authentication audit writes.
**Issue found or verification performed:** Initial grant uniqueness would have prevented a re-grant after revocation, and a `UserDetails` principal could retain the password hash in the session. Changed the grant index and made principals erase or omit password hashes. Static checks only; Maven/Docker/runtime checks are unavailable here.
**Correction and evidence:** Changes are in `backend/src/main/resources/db/migration/V1__initial_schema.sql`, `backend/src/main/java/com/trustvault/users/AuthenticatedUser.java`, and `backend/src/main/java/com/trustvault/security/CurrentAccountFilter.java`. Static JSON/XML, Vite config syntax, and file/schema assertions were checked; Java tests were added but not run.
**Commit:** Pending; no commit has been created.
