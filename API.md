# API

Current health, authentication, administrator account, and initial document routes are:

| Method | Path | Purpose | Access | Audit | Status |
|---|---|---|---|---|---|
| GET | `/api/health` | Basic process health response | Public; contains no sensitive data | None | Implemented, not runtime-tested in this environment |
| GET | `/api/auth/csrf` | Create/read browser CSRF token | Public; token is bound to the browser session | None | Implemented; runtime behavior not yet tested |
| POST | `/api/auth/login` | Authenticate using URL-encoded `email` and `password` | Public, session CSRF token required | `AUTH_LOGIN` allowed/denied; submitted email is not recorded | Implemented; runtime behavior not yet tested |
| GET | `/api/auth/me` | Return current user ID, email, display name, role | Authenticated session; account state refreshed from DB per request | None | Implemented; runtime behavior not yet tested |
| POST | `/api/auth/logout` | Invalidate current session | Authenticated session, CSRF token required | `AUTH_LOGOUT` allowed event | Implemented; runtime behavior not yet tested |
| GET | `/api/admin/users` | List account summaries | Authenticated administrator only | None | Implemented; manual endpoint verification pending |
| POST | `/api/admin/users` | Create an owner or viewer | Authenticated administrator only; session CSRF token required | `ADMIN_USER_CREATE` allowed event | Implemented; automated service tests added, runtime endpoint verification pending |
| PATCH | `/api/admin/users/{userId}/status` | Activate or deactivate an owner/viewer | Authenticated administrator only; session CSRF token required; cannot change own or administrator status | `ADMIN_USER_STATUS` allowed event | Implemented; automated service tests added, runtime endpoint verification pending |
| GET | `/api/documents` | List active documents owned by or currently granted to the signed-in user | Authenticated OWNER or VIEWER; administrator has no content access | `DOCUMENT_LIST` allowed/denied | Implemented; frontend build passed; backend tests pending |
| POST | `/api/documents` | Upload an owned PDF | Authenticated OWNER only; session CSRF token required; validates title, category, size, extension, claimed MIME, and PDF signature | `DOCUMENT_UPLOAD` allowed/denied/failed | Implemented; automated verification pending |
| GET | `/api/documents/{documentId}/download` | Download an active PDF | Authenticated OWNER or VIEWER with a current READ/EDIT grant; owner/grant, active status, and account state are checked on every request; non-authorized and missing IDs both return 404 | `DOCUMENT_DOWNLOAD` allowed/denied/failed | Implemented; service tests added, local Maven run pending |
| GET | `/api/documents/eligible-viewers` | List active Viewer accounts available for sharing | Authenticated OWNER; no administrator content privilege | `GRANT_LIST` | Implemented; local runtime verification pending |
| GET | `/api/documents/{documentId}/grants` | List active, unexpired grants for one owned document | Authenticated OWNER who owns the active document | `GRANT_LIST` | Implemented; local runtime verification pending |
| PUT | `/api/documents/{documentId}/grants/{userId}` | Create, change, or restore a named READ/EDIT grant | Authenticated OWNER who owns the active document; target must be an active VIEWER; CSRF required | `DOCUMENT_GRANT` | Implemented; local runtime verification pending |
| DELETE | `/api/documents/{documentId}/grants/{userId}` | Revoke a named grant | Authenticated OWNER who owns the active document; CSRF required | `DOCUMENT_REVOKE` | Implemented; local runtime verification pending |
| PATCH | `/api/documents/{documentId}` | Update title and category | Owner or active Viewer with current EDIT grant; CSRF required | `DOCUMENT_EDIT` | Implemented; local runtime verification pending |

Login accepts `application/x-www-form-urlencoded` fields `email` and `password`; failures use the same generic message. `/api/auth/csrf` returns the session-bound token. Include it as `X-CSRF-TOKEN` on POST requests. Obtain a fresh token after login and logout because Spring Security clears the previous token when authentication changes.

Search, soft deletion, document details, and admin audit views are not implemented yet.

## Initial document endpoints

`POST /api/documents` is restricted in the security filter chain to `ROLE_OWNER`, then the service rechecks the database account is active and has OWNER role. It accepts `multipart/form-data` fields `title`, `category`, and `file`. Files must be non-empty PDFs no larger than 10 MiB; filename extension, claimed `application/pdf` MIME type, and `%PDF-` signature are checked. The server stores bytes under a generated UUID key in the configured private local directory (`TRUSTVAULT_STORAGE_DIR`, default `./var/private-documents`). The returned summary excludes checksum and storage key. Request multipart limits are configured at 10 MiB per file and 11 MiB per request.

`GET /api/documents` scopes the query to active documents owned by the actor or with a current, unrevoked, unexpired grant. The returned summary includes only safe metadata and capability flags; it omits owner ID and storage key. Administrators do not receive document-content access.

`GET /api/documents/{documentId}/download` loads an active record, checks the current account and current ownership/grant, then reads private bytes. A VIEWER's current READ or EDIT grant permits download. It returns an attachment with `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. A non-authorized actor and a nonexistent ID share the same 404 response. No public or signed file URLs are issued. Because each request queries the current grant, revocation blocks the next request using the same session.

Grant endpoints are owner-only and verify document ownership on the server. Only active VIEWER accounts can receive grants. `PUT` accepts `{"permission":"READ"}` or `{"permission":"EDIT"}`. EDIT includes download/READ access and enables metadata updates through PATCH. Grants are perpetual in this MVP; the schema supports expiry, but no expiry field is accepted by this API. Revoked grants can be re-created, and an existing unrevoked grant can have its permission changed. All grant mutations and audit writes share a transaction.

Automated tests cover upload validation, safe upload summaries, denial of another owner's download, and current-grant download allow/deny decisions. The user ran `mvn test` on 2026-10-19: all 14 backend tests passed before the checksum mapping correction and sharing work. Rerun tests after the current changes. These are service/unit tests; HTTP authorization, PostgreSQL integration, and deployed storage protections still require verification.

## Administrator account endpoints

All `/api/admin/**` paths are restricted in the Spring Security filter chain to `ROLE_ADMINISTRATOR`; a role supplied in request JSON is never used to establish authority. Unauthenticated requests receive 401 and are audited as `ADMIN_USER_ACCESS` with `AUTHENTICATION_REQUIRED`. Authenticated users without the administrator role receive 403 and are audited with `INSUFFICIENT_ROLE`.

`POST /api/admin/users` accepts JSON fields `email`, `displayName`, `role` (`OWNER` or `VIEWER` only), and `password`. The service trims and lowercases email, validates lengths and format, rejects duplicate email, hashes the password with the configured BCrypt encoder, and never returns the password or hash. Passwords must contain at least 12 characters and no more than 72 UTF-8 bytes. Success returns 201 and a safe account summary; validation errors return 400 and duplicate email returns 409. The create and audit insert share a database transaction.

`PATCH /api/admin/users/{userId}/status` accepts `{"active": true|false}`. Only non-administrator accounts can be changed, and an administrator cannot change their own status. The update and audit insert share a transaction. Inactive users are rejected by the existing per-request current-account filter, so their next authenticated request is denied.

`GET /api/admin/users` returns only ID, email, display name, role, and active status. It never returns password hashes. Current tests cover account normalization, password encoding, duplicate email, role restrictions, and self-deactivation. HTTP-level authorization, CSRF, and runtime audit behavior still require verification against the running application.
