package com.snapitt.backend_service.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * VerifyOtpRequest - OTP Verification Request DTO
 *
 * HTTP Endpoint: POST /v1/auth/forgot-password/verify
 * Content-Type: application/json
 *
 * Second step of three-step password reset flow.
 * User provides email + OTP code received via email to validate OTP.
 *
 * Password Reset Flow (3 steps):
 * 1. **Init**: POST /forgot-password/init
 *    - Generates OTP, sends to email
 * 2. **Verify (this endpoint)**: POST /forgot-password/verify with email + code
 *    - Validates OTP exists, not expired, and code matches
 *    - Returns 200 OK (OTP verified, ready to reset password)
 * 3. **Reset**: POST /forgot-password/reset with email + code + new password
 *    - Uses validated OTP to reset password
 *    - Updates password hash and deletes OTP
 *
 * Example Request:
 * ```json
 * {
 *   "email": "john@example.com",
 *   "code": "123456"
 * }
 * ```
 *
 * Response:
 * - Status: 200 OK
 * - Body: Empty (void response)
 * - Meaning: OTP verified successfully, proceed to reset password
 *
 * OTP Details:
 * - Format: 6-digit decimal string
 * - Validity: 5 minutes from creation
 * - Obtained: From email received after POST /forgot-password/init
 * - Delivery: Email (or console log in development)
 * - Uniqueness: Not required (multiple OTPs per email allowed)
 *
 * Validation Steps:
 * 1. Find most recent OtpEntity for email
 * 2. Check OTP exists (throw 400 INVALID_OTP if not found)
 * 3. Check OTP not expired (Instant.now() < expiresAt)
 * 4. Check code matches exactly (case-sensitive, numeric string)
 * 5. Return 200 OK (OTP valid, caller can now reset password)
 *
 * Security Notes:
 * - OTP is 6 digits = 1 million possibilities (susceptible to brute force)
 * - Rate limiting required to prevent attacks (implement at API layer)
 * - Recommendation: Max 5 attempts per OTP before lockout
 * - Error messages reveal OTP status (invalid vs expired) by design
 * - OTP not deleted after verification (used again in reset endpoint)
 * - Email case-insensitive, OTP code exact match
 *
 * Error Responses:
 * - 400 Bad Request: OTP not found, expired, or code mismatch
 *   - INVALID_OTP: Both for missing OTP and code mismatch
 *   - OTP_EXPIRED: Explicit error when age > 5 minutes
 *
 * Workflow:
 * ```
 * User -> POST /forgot-password/init [email]
 *    -> Receives OTP via email (e.g., "123456")
 * User -> POST /forgot-password/verify [email, code="123456"]
 *    -> Response: 200 OK (validated)
 * User -> POST /forgot-password/reset [email, code="123456", password]
 *    -> Response: 200 OK (password reset)
 * ```
 *
 * @see VerifyOtpRequest
 * @see AuthController#verifyOtp(VerifyOtpRequest)
 * @see AuthService#verifyOtp(VerifyOtpRequest)
 * @see ForgotPasswordInitRequest
 * @see ResetPasswordRequest
 * @since 1.0.0
 */
@Data
public class VerifyOtpRequest {
    /**
     * Email address associated with OTP.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Format: Valid email per RFC 5322 (enforced via @Email)
     * - Case-insensitive lookup: Email matching ignores case
     * - Must match email from init step: Used to find correct OTP
     *
     * Purpose:
     * - OTP retrieval: Used to find OtpEntity with matching email
     * - Account identification: Determines which user's OTP to verify
     *
     * Consistency:
     * - Must match email from POST /forgot-password/init request
     * - If different email: Returns 400 INVALID_OTP (no OTP found)
     * - Example: Initiated with "john@example.com", must verify with same email
     *
     * @see OtpEntity#email
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Six-digit one-time password code.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Format: Exactly 6 decimal digits (0-9)
     * - Matches: Exact string match against stored OTP code
     * - Case-sensitive: Currently always numeric (N/A for case)
     *
     * Acquisition:
     * - Source: Email received from POST /forgot-password/init
     * - Format in email: Plain text, e.g., "123456"
     * - Validity: 5 minutes from OTP creation
     *
     * Examples:
     * - "123456"
     * - "000001" (leading zeros preserved as string)
     * - "999999"
     *
     * Verification Logic:
     * 1. Lookup latest OtpEntity by email
     * 2. Check expiration: Instant.now() < expiresAt
     * 3. Compare code: code.equals(submittedCode)
     * 4. Exact match required (no partial credit)
     *
     * Error Handling:
     * - Code mismatch: 400 INVALID_OTP
     * - OTP expired: 400 OTP_EXPIRED
     * - OTP not found: 400 INVALID_OTP
     * - Generic messages prevent enumeration attacks
     *
     * Brute-Force Prevention:
     * - 1 million possibilities (6 digits)
     * - Rate limiting required: Max 5 attempts per OTP
     * - Exponential backoff or temporary lockout recommended
     * - Currently not implemented (add at API gateway)
     *
     * @see OtpEntity#code
     */
    @NotBlank(message = "OTP Code is required")
    private String code;
}