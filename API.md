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
| GET | `/api/documents` | List the signed-in owner's active documents | Authenticated; owners see their own documents; other roles receive an empty list | `DOCUMENT_LIST` allowed/denied | Implemented; automated verification pending |
| POST | `/api/documents` | Upload an owned PDF | Authenticated OWNER only; session CSRF token required; validates title, category, size, extension, claimed MIME, and PDF signature | `DOCUMENT_UPLOAD` allowed/denied/failed | Implemented; automated verification pending |
| GET | `/api/documents/{documentId}/download` | Download an active owned PDF | Authenticated OWNER; owner ID and active state are checked on every request; non-owner and missing IDs both return 404 | `DOCUMENT_DOWNLOAD` allowed/denied/failed | Implemented; automated verification pending |

Login accepts `application/x-www-form-urlencoded` fields `email` and `password`; failures use the same generic message. `/api/auth/csrf` returns the session-bound token. Include it as `X-CSRF-TOKEN` on POST requests. Obtain a fresh token after login and logout because Spring Security clears the previous token when authentication changes.

The initial document slice intentionally supports owners' own documents only. Viewer grants, metadata editing, search, soft deletion, and admin audit views are not implemented yet.

## Initial document endpoints

`POST /api/documents` is restricted in the security filter chain to `ROLE_OWNER`, then the service rechecks the database account is active and has OWNER role. It accepts `multipart/form-data` fields `title`, `category`, and `file`. Files must be non-empty PDFs no larger than 10 MiB; filename extension, claimed `application/pdf` MIME type, and `%PDF-` signature are checked. The server stores bytes under a generated UUID key in the configured private local directory (`TRUSTVAULT_STORAGE_DIR`, default `./var/private-documents`). The returned summary excludes checksum and storage key. Request multipart limits are configured at 10 MiB per file and 11 MiB per request.

`GET /api/documents` scopes the query to the authenticated owner's ID and active status. Other roles currently receive an empty list until grant-backed listing is implemented.

`GET /api/documents/{documentId}/download` loads an active record, checks the current authenticated account and owner ID, then reads private bytes. It returns an attachment with `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. A non-owner and a nonexistent ID share the same 404 response. No public or signed file URLs are issued.

Automated tests cover invalid PDF signature rejection, an upload's safe summary, and denial of another owner's download before storage bytes are read. The user ran `mvn test` on 2026-10-19: all 14 backend tests passed. These are service/unit tests; HTTP authorization, PostgreSQL integration, and deployed storage protections still require verification.

## Administrator account endpoints

All `/api/admin/**` paths are restricted in the Spring Security filter chain to `ROLE_ADMINISTRATOR`; a role supplied in request JSON is never used to establish authority. Unauthenticated requests receive 401 and are audited as `ADMIN_USER_ACCESS` with `AUTHENTICATION_REQUIRED`. Authenticated users without the administrator role receive 403 and are audited with `INSUFFICIENT_ROLE`.

`POST /api/admin/users` accepts JSON fields `email`, `displayName`, `role` (`OWNER` or `VIEWER` only), and `password`. The service trims and lowercases email, validates lengths and format, rejects duplicate email, hashes the password with the configured BCrypt encoder, and never returns the password or hash. Passwords must contain at least 12 characters and no more than 72 UTF-8 bytes. Success returns 201 and a safe account summary; validation errors return 400 and duplicate email returns 409. The create and audit insert share a database transaction.

`PATCH /api/admin/users/{userId}/status` accepts `{"active": true|false}`. Only non-administrator accounts can be changed, and an administrator cannot change their own status. The update and audit insert share a transaction. Inactive users are rejected by the existing per-request current-account filter, so their next authenticated request is denied.

`GET /api/admin/users` returns only ID, email, display name, role, and active status. It never returns password hashes. Current tests cover account normalization, password encoding, duplicate email, role restrictions, and self-deactivation. HTTP-level authorization, CSRF, and runtime audit behavior still require verification against the running application.
