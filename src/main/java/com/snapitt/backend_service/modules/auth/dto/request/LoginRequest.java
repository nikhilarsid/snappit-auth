package com.snapitt.backend_service.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * LoginRequest - User Login Request DTO
 *
 * HTTP Endpoint: POST /v1/auth/login
 * Content-Type: application/json
 *
 * Request body for local authentication using username/email and password.
 * Requires existing account with LOCAL auth type (email+password signup).
 *
 * Validation:
 * - usernameOrEmail: Required, non-blank
 * - password: Required, non-blank
 * - No format validation (flexible username/email detection)
 * - Case-insensitive username/email matching
 *
 * Authentication Flow:
 * 1. Find user by username OR email (dual-lookup)
 * 2. Verify user has LOCAL auth type (not Google-only accounts)
 * 3. Compare submitted password with stored bcrypt hash
 * 4. Generate JWT token if credentials valid
 * 5. Set HttpOnly, Secure cookie with token
 * 6. Return 200 OK with UserEntity and token
 *
 * Example Request:
 * ```json
 * {
 *   "usernameOrEmail": "john_doe123",
 *   "password": "SecurePass@123"
 * }
 * ```
 * OR
 * ```json
 * {
 *   "usernameOrEmail": "john@example.com",
 *   "password": "SecurePass@123"
 * }
 * ```
 *
 * Success Response:
 * - Status: 200 OK
 * - Body: AuthResponse containing UserEntity and JWT token
 * - Cookie: auth_token with JWT (HttpOnly, Secure)
 *
 * Error Responses:
 * - 401 Unauthorized: Invalid username/password combination (generic message)
 * - 403 Forbidden: Account uses Google OAuth only (GOOGLE_AUTH_REQUIRED)
 *
 * Security Notes:
 * - Error messages are intentionally generic to prevent email enumeration
 * - Both "user not found" and "password incorrect" return same 401 error
 * - Password verification uses constant-time comparison (bcrypt)
 * - Failed login attempts not rate-limited (implement at API gateway)
 *
 * @see AuthController#login(LoginRequest)
 * @see AuthService#login(LoginRequest)
 * @since 1.0.0
 */
@Data
public class LoginRequest {
    /**
     * Username or email address for account lookup.
     *
     * Accepts Either:
     * - Username: Alphanumeric, underscore, dash, dot (3-20 chars)
     * - Email: Valid email format (RFC 5322)
     *
     * Lookup Order:
     * 1. Try finding user by username (exact match, case-insensitive)
     * 2. Fallback: Try finding user by email (exact match, case-insensitive)
     * 3. If both fail: 401 Unauthorized
     *
     * Examples:
     * - "john_doe123" (via username)
     * - "john@example.com" (via email)
     * - Both resolve to same user if registered
     *
     * No format validation enforced (accepts any non-blank string):
     * AuthService.login handles ambiguous input gracefully
     *
     * @see UserRepository#findByUsername(String)
     * @see UserRepository#findByEmail(String)
     */
    @NotBlank
    private String usernameOrEmail;

    /**
     * Password for authentication.
     *
     * Verification:
     * - Matched against bcrypt hash in AuthEntity.passwordHash
     * - Constant-time comparison prevents timing attacks
     * - Case-sensitive (passwords are case-sensitive)
     * - No length restrictions (accepts full bcrypt hash verification)
     *
     * Security:
     * - Transmitted via HTTPS only (must use TLS)
     * - Not stored, only matched against hash
     * - Invalid password returns 401 with generic message
     * - Incorrect password and missing user indistinguishable (by design)
     *
     * Password Reset:
     * - Forgotten password: Use POST /v1/auth/forgot-password/init
     * - OTP sent to registered email
     * - Reset via POST /v1/auth/forgot-password/reset with OTP
     *
     * @see AuthEntity#passwordHash
     * @see org.springframework.security.crypto.password.PasswordEncoder#matches(CharSequence, String)
     */
    @NotBlank
    private String password;
}