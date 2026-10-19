# API

No document-management API endpoints are implemented yet. Current health, authentication, and administrator account routes are:

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

Login accepts `application/x-www-form-urlencoded` fields `email` and `password`; failures use the same generic message. `/api/auth/csrf` returns the session-bound token. Include it as `X-CSRF-TOKEN` on POST requests. Obtain a fresh token after login and logout because Spring Security clears the previous token when authentication changes.

Before adding a document route, document its authentication, required role/grant, exact policy enforcement point, validation, success/denial behavior, audit event, and tests here.

## Administrator account endpoints

All `/api/admin/**` paths are restricted in the Spring Security filter chain to `ROLE_ADMINISTRATOR`; a role supplied in request JSON is never used to establish authority. Unauthenticated requests receive 401 and are audited as `ADMIN_USER_ACCESS` with `AUTHENTICATION_REQUIRED`. Authenticated users without the administrator role receive 403 and are audited with `INSUFFICIENT_ROLE`.

`POST /api/admin/users` accepts JSON fields `email`, `displayName`, `role` (`OWNER` or `VIEWER` only), and `password`. The service trims and lowercases email, validates lengths and format, rejects duplicate email, hashes the password with the configured BCrypt encoder, and never returns the password or hash. Passwords must contain at least 12 characters and no more than 72 UTF-8 bytes. Success returns 201 and a safe account summary; validation errors return 400 and duplicate email returns 409. The create and audit insert share a database transaction.

`PATCH /api/admin/users/{userId}/status` accepts `{"active": true|false}`. Only non-administrator accounts can be changed, and an administrator cannot change their own status. The update and audit insert share a transaction. Inactive users are rejected by the existing per-request current-account filter, so their next authenticated request is denied.

`GET /api/admin/users` returns only ID, email, display name, role, and active status. It never returns password hashes. Current tests cover account normalization, password encoding, duplicate email, role restrictions, and self-deactivation. HTTP-level authorization, CSRF, and runtime audit behavior still require verification against the running application.
