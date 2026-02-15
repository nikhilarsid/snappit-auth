package com.snapitt.backend_service.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ResetPasswordRequest - Password Reset Completion Request DTO
 *
 * HTTP Endpoint: POST /v1/auth/forgot-password/reset
 * Content-Type: application/json
 *
 * Third step of three-step password reset flow.
 * User submits new password with email + OTP code to complete reset.
 *
 * Password Reset Flow (3 steps):
 * 1. **Init**: POST /forgot-password/init with email
 *    - Generates OTP, sends to email
 * 2. **Verify**: POST /forgot-password/verify with email + OTP code
 *    - Validates OTP exists, not expired, and code matches
 * 3. **Reset (this endpoint)**: POST /forgot-password/reset
 *    - Validates OTP again (via verifyOtp call)
 *    - Validates password strength
 *    - Creates or updates AuthEntity with new password hash
 *    - Deletes OTP to prevent reuse
 *    - Returns 200 OK (password reset complete)
 *
 * Example Request:
 * ```json
 * {
 *   "email": "john@example.com",
 *   "code": "123456",
 *   "newPassword": "NewSecurePass@456"
 * }
 * ```
 *
 * Response:
 * - Status: 200 OK
 * - Body: Empty (void response)
 * - Meaning: Password reset successful, user can login with new password
 *
 * Password Reset Steps:
 * 1. Validate new password strength
 *    - Min 8 chars, uppercase, lowercase, digit, special char
 *    - Throw 400 WEAK_PASSWORD if fails
 * 2. Verify OTP (reuses verifyOtp method)
 *    - Checks email, code validity, expiration
 *    - Throw 400 INVALID_OTP or OTP_EXPIRED if fails
 * 3. Find UserEntity by email
 *    - Throw 404 USER_NOT_FOUND if not found
 * 4. Update or create AuthEntity
 *    - If LOCAL auth exists: Update passwordHash
 *    - If no LOCAL auth: Create new AuthEntity with LOCAL type
 *    - Enables password login for Google-only accounts
 * 5. Delete OtpEntity
 *    - Prevents OTP reuse
 *    - Cleanup after successful reset
 * 6. Return 200 OK
 *
 * Security Considerations:
 * - Password strength enforced (prevents weak passwords)
 * - OTP re-verified (prevents invalid reset attempts)
 * - OTP deleted after reset (single-use guarantee)
 * - Bcrypt hashing with cost 12 (slow, resistant to brute force)
 * - Transaction: Password + OTP deletion atomic (both or neither)
 *
 * Error Responses:
 * - 400 Bad Request:
 *   - WEAK_PASSWORD: New password doesn't meet strength requirements
 *   - INVALID_OTP: OTP not found or code mismatch
 *   - OTP_EXPIRED: OTP older than 5 minutes
 * - 404 Not Found:
 *   - USER_NOT_FOUND: Email not registered (defensive check)
 *
 * Edge Cases:
 * - Google-only account: Creates LOCAL auth (enables password login)
 * - Existing LOCAL auth: Updates password (replaces old hash)
 * - Multiple OTPs: Uses latest OTP (ordered by expiresAt DESC)
 * - OTP delay: User can wait up to 5 minutes to reset
 * - Same password: New password can be same as old (allowed, still hashed)
 * - Concurrent requests: Atomic transaction prevents race conditions
 *
 * Future Enhancements:
 * - Password history: Prevent reusing recent passwords
 * - Session invalidation: Force re-login after reset
 * - Notification: Email confirmation after successful reset
 * - 2FA requirement: For critical account reset
 * - IP tracking: Alert user of password reset from new location
 *
 * Workflow Example:
 * ```
 * User -> POST /forgot-password/init [email="john@example.com"]
 * System -> Generates OTP "123456", sends to email
 * User -> Reads email, extracts OTP
 * User -> POST /forgot-password/verify [email, code="123456"]
 * System -> Response: 200 OK (OTP valid)
 * User -> POST /forgot-password/reset [email, code="123456", password="NewPass@789"]
 * System -> Response: 200 OK (password reset)
 * User -> Can now login with POST /login [email, password="NewPass@789"]
 * ```
 *
 * @see AuthController#resetPassword(ResetPasswordRequest)
 * @see AuthService#resetPassword(ResetPasswordRequest)
 * @see ForgotPasswordInitRequest
 * @see VerifyOtpRequest
 * @since 1.0.0
 */
@Data
public class ResetPasswordRequest {
    /**
     * Email address for password reset.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Format: Valid email per RFC 5322 (enforced via @Email)
     * - Case-insensitive lookup: Email matching ignores case
     * - Must match init email: Should be same email from POST /forgot-password/init
     * - Must exist: OtpRepository lookup requires this email
     *
     * Purpose:
     * - OTP retrieval: Used to find OtpEntity with matching email
     * - User identification: Determines which user's password to reset
     * - Account lookup: Finds UserEntity to update AuthEntity
     *
     * Consistency Check:
     * - Should match email from POST /forgot-password/init
     * - Should match email from POST /forgot-password/verify
     * - If different: OTP lookup will fail (400 INVALID_OTP)
     *
     * @see UserRepository#findByEmail(String)
     * @see OtpRepository#findTopByEmailOrderByExpiresAtDesc(String)
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * One-time password code for verification.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Format: 6-digit decimal string (e.g., "123456")
     * - Exact match: Must match OTP code in database exactly
     * - Expiration: Must be within 5 minutes of creation
     *
     * Verification:
     * 1. Find latest OtpEntity for email
     * 2. Check expiration: Instant.now() < expiresAt
     * 3. Compare code: code.equals(submittedCode)
     * 4. All checks must pass (throw 400 if any fail)
     *
     * Purpose:
     * - Anti-CSRF: Ensures user has access to email
     * - Replay protection: Single-use OTP (deleted after reset)
     * - Brute-force resistance: 6 digits, 5-minute window
     *
     * Revalidation:
     * - verifyOtp() is called again before updating password
     * - Ensures OTP hasn't expired between verify and reset steps
     * - User may wait up to 5 minutes between verify and reset
     *
     * OTP Consumption:
     * - After successful reset: OTP is deleted from database
     * - Prevents reuse: Same OTP cannot be used twice
     * - Immediate: Deletion happens within same transaction
     *
     * @see OtpEntity#code
     * @see AuthService#verifyOtp(VerifyOtpRequest)
     */
    @NotBlank(message = "OTP Code is required")
    private String code;

    /**
     * New password for the account.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Length: Minimum 8 characters enforced by ValidationUtils
     * - Complexity: Must contain:
     *   - At least one UPPERCASE letter (A-Z)
     *   - At least one lowercase letter (a-z)
     *   - At least one digit (0-9)
     *   - At least one special character (!@#$%^&*)
     * - Invalid: Throw 400 WEAK_PASSWORD if validation fails
     *
     * Password Hashing:
     * - Algorithm: Bcrypt with cost factor 12
     * - Hash length: 60 characters
     * - Format: $2a$12$[salt][hash]
     * - Irreversible: Cannot decrypt to original password
     *
     * Security Properties:
     * - Brute-force resistant: Cost factor 12 = ~0.3 seconds per hash
     * - Salt included: Each password gets unique salt (stored in hash)
     * - Adaptive: Cost factor can be increased as hardware improves
     * - No plaintext storage: Only hash stored in AuthEntity.passwordHash
     *
     * Password Requirements:
     * - Min entropy: 60 bits (approximated by strength rules)
     * - Recommendations: 12-16 random characters or long passphrase
     * - Avoid: Dictionary words, sequential chars, birthdate, personal info
     * - Examples of strong passwords:
     *   - "Tr0pic@l!Night" (mix of requirements)
     *   - "Coffee&Toast#2024" (memorable + strong)
     *   - "Ux$7kL9@mP2q" (random, complex)
     *
     * Edge Cases:
     * - Same as old: Allowed (still hashed/updated)
     * - Too similar: Not checked (future enhancement)
     * - After reset: User must login with new password
     *
     * Future Enhancements:
     * - Password history: Prevent reusing old passwords
     * - Strength meter: Frontend visual feedback
     * - Passkey support: Alternative to password-based auth
     * - Breached password check: Cross-reference with known breaches
     *
     * @see ValidationUtils#isValidPassword(String)
     * @see org.springframework.security.crypto.password.PasswordEncoder
     */
    @NotBlank(message = "New Password is required")
    private String newPassword;
}