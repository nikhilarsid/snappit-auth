package com.snapitt.backend_service.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ForgotPasswordInitRequest - Password Reset Initialization Request DTO
 *
 * HTTP Endpoint: POST /v1/auth/forgot-password/init
 * Content-Type: application/json
 *
 * First step of three-step password reset flow.
 * User provides registered email to receive one-time password (OTP).
 *
 * Password Reset Flow (3 steps):
 * 1. **Init (this endpoint)**: POST /forgot-password/init with email
 *    - Validates email exists
 *    - Generates 6-digit OTP
 *    - Sends OTP to email (currently prints to console)
 *    - Returns 200 OK (no sensitive information leaked)
 * 2. **Verify**: POST /forgot-password/verify with email + OTP code
 *    - Validates OTP exists, not expired, and code matches
 *    - Returns 200 OK (OTP verified, ready to reset)
 * 3. **Reset**: POST /forgot-password/reset with email + OTP code + new password
 *    - Validates password strength
 *    - Updates AuthEntity with new bcrypt-hashed password
 *    - Deletes OTP (prevents reuse)
 *    - Returns 200 OK (password reset complete)
 *
 * Example Request:
 * ```json
 * {
 *   "email": "john@example.com"
 * }
 * ```
 *
 * Response:
 * - Status: 200 OK
 * - Body: Empty (void response)
 * - Console Log: "OTP for john@example.com: 123456" (development only)
 * - Email: OTP sent to registered email (when EmailService enabled)
 *
 * Security Notes:
 * - Always returns 200 OK (prevents email enumeration)
 * - User provides email (not username), must know registration email
 * - OTP is 6 digits, valid for 5 minutes only
 * - OTP delivery via email only (no SMS in current implementation)
 * - Rate limiting recommended (e.g., 3 OTP requests per email per hour)
 *
 * Error Handling:
 * - Currently throws 404 if email not found (reveals email enumeration)
 * - Future improvement: Always return 200 OK, silently fail if email not found
 * - Batch prevention: Add rate limiting per email or IP address
 *
 * Edge Cases:
 * - Multiple OTP requests: Latest OTP replaces previous (only last is valid)
 * - OTP expiration: 5 minutes (hardcoded in initForgotPassword)
 * - Email case-insensitivity: Lookups are case-insensitive (stored as-is)
 * - Unregistered email: Currently returns 404 (future: silent success)
 *
 * Next Steps:
 * - User receives OTP (via email or console log)
 * - Call POST /forgot-password/verify with email + OTP code
 * - Call POST /forgot-password/reset with email + OTP + new password
 *
 * @see AuthController#initForgotPassword(ForgotPasswordInitRequest)
 * @see AuthService#initForgotPassword(ForgotPasswordInitRequest)
 * @see VerifyOtpRequest
 * @see ResetPasswordRequest
 * @since 1.0.0
 */
@Data
public class ForgotPasswordInitRequest {
    /**
     * User's registered email address.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Format: Valid email per RFC 5322 (enforced via @Email)
     * - Case-insensitive lookup: Email matching ignores case
     * - Must exist: Throws 404 if email not registered (security issue)
     *
     * Purpose:
     * - Account identification: Used to find user in UserRepository
     * - OTP delivery: OTP sent to this email address
     * - Password reset: Required for verify/reset endpoints
     *
     * Examples:
     * - john@example.com
     * - JOHN@EXAMPLE.COM (treated as john@example.com)
     * - john.doe+recovery@example.com
     *
     * Error Handling:
     * - Email not found: Currently returns 404 NOT_FOUND
     * - Future improvement: Return 200 OK (silent) to prevent enumeration
     *
     * Security Concerns:
     * - 404 error reveals whether email is registered (enumeration)
     * - Recommendation: Use generic "Check your email for reset link"
     * - Implementation: Add rate limiting to prevent brute-force enumeration
     * - Alternative: Use username instead of email for initiation
     *
     * @see UserRepository#findByEmail(String)
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}