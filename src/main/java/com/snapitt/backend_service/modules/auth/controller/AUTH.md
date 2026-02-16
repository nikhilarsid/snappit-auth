# Auth Module — API & Error Mapping

Location: `src/main/java/com/snapitt/backend_service/modules/auth/controller`

Overview
- Controller: `AuthController`
- Services: `AuthService`, `GoogleAuthService`
- Exception handling: `GlobalExceptionHandler`, `AuthException`

Endpoints

- POST /v1/auth/signup
  - Request: JSON { username, email, password, name }
  - Success: 201 Created, sets cookie `token` (HttpOnly, Secure, Path=/, Max-Age=604800)
  - Errors:
    - 400 VALIDATION_ERROR (bad input)
    - 400 WEAK_PASSWORD (password fails strength rules)
    - 409 USERNAME_ALREADY_EXISTS
    - 409 EMAIL_ALREADY_EXISTS
    - 500 INTERNAL_SERVER_ERROR

- POST /v1/auth/login
  - Request: JSON { usernameOrEmail, password }
  - Success: 200 OK, sets cookie `token`
  - Errors:
    - 400 VALIDATION_ERROR
    - 401 INVALID_CREDENTIALS

- POST /v1/auth/google
  - Request: JSON { idToken }
  - Success: 200 OK, sets cookie `token`, body { isNewUser: boolean }
  - Errors:
    - 400 VALIDATION_ERROR (malformed JSON / missing idToken)
    - 401 INVALID_GOOGLE_TOKEN (verification failed)
    - 409 EMAIL_ALREADY_REGISTERED (if auto-linking disabled)

- POST /v1/auth/forgot-password/init
  - Request: JSON { email }
  - Success: 200 OK
  - Errors:
    - 400 VALIDATION_ERROR
    - 404 USER_NOT_FOUND

- POST /v1/auth/forgot-password/verify
  - Request: JSON { email, code }
  - Success: 200 OK
  - Errors:
    - 400 INVALID_OTP
    - 400 OTP_EXPIRED

- POST /v1/auth/forgot-password/reset
  - Request: JSON { email, code, newPassword }
  - Success: 200 OK
  - Errors:
    - 400 VALIDATION_ERROR
    - 400 WEAK_PASSWORD
    - 404 USER_NOT_FOUND

- POST /v1/auth/logout
  - Success: 200 OK, clears cookie `token`

Error codes & mappings (high level)
- VALIDATION_ERROR: 400
- WEAK_PASSWORD: 400
- INVALID_CREDENTIALS: 401
- INVALID_GOOGLE_TOKEN / GOOGLE_AUTH_FAILED: 401
- GOOGLE_AUTH_REQUIRED: 403
- USER_NOT_FOUND: 404
- INVALID_OTP / OTP_EXPIRED: 400
- USERNAME_ALREADY_EXISTS / EMAIL_ALREADY_EXISTS: 409
- USERNAME_OR_EMAIL_ALREADY_EXISTS: 409 (fallback duplicate-key mapping)
- COOKIE_ERROR: 500
- INTERNAL_SERVER_ERROR: 500

Notes about recent changes
- `AuthController#setCookie` now handles null tokens and catches `IllegalArgumentException`, logging and converting to `AuthException` with code `COOKIE_ERROR`.
- `GlobalExceptionHandler` now maps:
  - `DuplicateKeyException` -> 409 with inferred `USERNAME_ALREADY_EXISTS` / `EMAIL_ALREADY_EXISTS` codes
  - `HttpMessageNotReadableException` -> 400 `VALIDATION_ERROR` (malformed JSON)
  - `IllegalArgumentException` -> 400 `VALIDATION_ERROR`

Tests added
- `src/test/java/com/snapitt/backend_service/modules/auth/service/AuthServiceTest.java` — unit tests for signup/login flows
- `src/test/java/com/snapitt/backend_service/modules/auth/controller/AuthControllerTest.java` — MockMvc tests for signup/login endpoints

Implementation notes
- Do not change service business logic when adding handlers; only validation and exception mapping were added.
- Cookie behavior: controller enforces `cookie.setSecure(true)` (production expectation). Adjust for local testing if needed.

If you want, I can extend tests to cover Google login, OTP flows, and duplicate-key handling.
