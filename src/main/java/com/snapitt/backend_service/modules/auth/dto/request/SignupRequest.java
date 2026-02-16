package com.snapitt.backend_service.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SignupRequest - User Registration Request DTO
 *
 * HTTP Endpoint: POST /v1/auth/signup
 * Content-Type: application/json
 *
 * Request body for user registration with email and password.
 * All fields are required and validated before processing.
 *
 * Validation Chain:
 * 1. Field-level (Jakarta Bean Validation annotations)
 *    - name: @NotBlank
 *    - username: @NotBlank, @Size(3-20 chars)
 *    - email: @NotBlank, @Email format
 *    - password: @NotBlank
 * 2. Service-level (AuthService.signup method)
 *    - Password strength: Min 8 chars, uppercase, lowercase, digit, special char
 *    - Username format: Alphanumeric, underscore, dash, dot only
 *    - Username uniqueness: Check UserRepository for duplicates
 *    - Email uniqueness: Check UserRepository for duplicates
 *
 * Example Request:
 * ```json
 * {
 *   "name": "John Doe",
 *   "username": "john_doe123",
 *   "email": "john@example.com",
 *   "password": "SecurePass@123"
 * }
 * ```
 *
 * Response:
 * - Status: 201 Created
 * - Body: AuthResponse containing created UserEntity and JWT token
 *
 * Error Responses:
 * - 400 Bad Request: Validation failed (weak password, invalid format)
 * - 409 Conflict: Username or email already in use
 *
 * @see AuthController#signup(SignupRequest)
 * @see AuthService#signup(SignupRequest)
 * @since 1.0.0
 */
@Data
public class SignupRequest {
    /**
     * User's full name (display name).
     * 
     * Validation:
     * - Required: Cannot be null or empty
     * - No format restrictions (supports international characters)
     * - Used in UserEntity.profile.name
     * - Public field, visible to all users
     *
     * Example: "John Doe", "李明", "María García"
     */
    @NotBlank(message = "Name is required")
    private String name;

    /**
     * Unique username for login and profile discovery.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Length: 3-20 characters (enforced via @Size)
     * - Format: Alphanumeric, underscore, dash, dot only (validated by ValidationUtils)
     * - Uniqueness: Must not exist in UserRepository (checked in signup service)
     * - Case-insensitive comparison: Treated as case-insensitive for lookups
     *
     * Examples:
     * - john_doe123
     * - john.doe
     * - john-doe
     * - jdoe
     *
     * Invalid Examples:
     * - "jo" (too short)
     * - "john_doe_with_very_long_name" (too long)
     * - "john@doe" (special char not allowed)
     * - "john doe" (spaces not allowed)
     *
     * @see ValidationUtils#isValidUsername(String)
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
    private String username;

    /**
     * User's email address (used for login and password recovery).
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Format: Valid email per RFC 5322 (enforced via @Email)
     * - Uniqueness: Must not exist in UserRepository (checked in signup service)
     * - Case-insensitive comparison: Stored and compared case-insensitively
     *
     * Examples:
     * - john@example.com
     * - john.doe+tag@example.com
     * - user123@domain.co.uk
     *
     * Security Notes:
     * - Used for password recovery (OTP sent to this email)
     * - Must be accessible by user (cannot use fake emails)
     * - In future: Email verification flow recommended
     *
     * Character Encoding: UTF-8
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * User's password for LOCAL authentication.
     * Bcrypt-hashed and stored in AuthEntity.passwordHash.
     *
     * Validation:
     * - Required: Cannot be null or empty
     * - Length: Minimum 8 characters (enforced by ValidationUtils)
     * - Complexity: Must contain:
     *   - At least one UPPERCASE letter (A-Z)
     *   - At least one lowercase letter (a-z)
     *   - At least one digit (0-9)
     *   - At least one special character (!@#$%^&*)
     *
     * Security:
     * - Transmitted via HTTPS only (must use TLS)
     * - Hashed with bcrypt cost factor 12 (adaptive)
     * - Never stored in plain text
     * - Never returned in API responses
     * - Password reset available via OTP flow
     *
     * Password Recommendations:
     * - Min entropy: 60 bits (approximated by strength requirements)
     * - Avoid: Dictionary words, sequential chars, birthdate, personal info
     * - Enable: 2FA/passkeys for additional security (future feature)
     *
     * Example Strong Passwords:
     * - "MyP@ssw0rd2024"
     * - "Secure#Pass123"
     * - "C0mpl3x&Password"
     *
     * @see ValidationUtils#isValidPassword(String)
     * @see org.springframework.security.crypto.password.PasswordEncoder
     */
    @NotBlank(message = "Password is required")
    private String password;
}