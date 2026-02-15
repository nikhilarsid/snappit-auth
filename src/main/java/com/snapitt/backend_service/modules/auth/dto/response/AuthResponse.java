package com.snapitt.backend_service.modules.auth.dto.response;

import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AuthResponse - Authentication Success Response DTO
 *
 * HTTP Status: 200 OK or 201 Created
 * Content-Type: application/json
 *
 * Response body for successful authentication (signup, login, Google OAuth).
 * Contains authenticated user details and JWT token for accessing protected resources.
 *
 * Response Scenarios:
 * - POST /v1/auth/signup: 201 Created with new user + token
 * - POST /v1/auth/login: 200 OK with authenticated user + token
 * - POST /v1/auth/google: 200 OK with user (new or existing) + token
 *
 * Example Response:
 * ```json
 * {
 *   "user": {
 *     "id": "507f1f77bcf86cd799439011",
 *     "username": "john_doe123",
 *     "email": "john@example.com",
 *     "profile": {
 *       "name": "John Doe",
 *       "bio": "Software engineer",
 *       "avatarUrl": "https://example.com/avatar.jpg"
 *     },
 *     "followersCount": 42,
 *     "followingCount": 25,
 *     "createdAt": "2024-01-15T10:30:00Z"
 *   },
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiI1MDdmMWY3N2JjZjg2Y2Q3OTk0MzkwMTEiLCJzdWIiOiJqb2huX2RvZTEyMyIsImlhdCI6MTcwNTMyODYwMCwiZXhwIjoxNzA1NDE1MDAwfQ.signature"
 * }
 * ```
 *
 * Cookie Usage:
 * - Token also set as HttpOnly, Secure cookie named "auth_token"
 * - Cookie Path: "/"
 * - Cookie MaxAge: 7 days (604800 seconds)
 * - HttpOnly: true (JavaScript cannot access, prevents XSS cookie theft)
 * - Secure: true (HTTPS only, prevents MITM cookie interception)
 * - SameSite: Not explicitly set (depends on Spring Security defaults)
 *
 * Token Details:
 * - Format: JWT (JSON Web Token) with three dot-separated parts
 * - Algorithm: Typically HS256 (HMAC with SHA-256)
 * - Validity: 24 hours from issuance (configurable via JwtService)
 * - Claims: userId, subject (username), issued-at, expiration
 * - Revocation: No built-in revocation (stateless design)
 *
 * Token Usage:
 * - HTTP Header: Authorization: Bearer [token]
 * - Cookie: Sent automatically with each request (if set)
 * - Protected Routes: Extracted via SecurityContextHolder
 * - ID Extraction: jwtService.extractUserId(token)
 *
 * User Details:
 * - Contains full UserEntity object (all public profile data)
 * - Fields visible to client:
 *   - id: MongoDB ObjectId (credentials)
 *   - username: Unique, used for @mentions and lookups
 *   - email: Used for password recovery (may be hidden in future)
 *   - profile.name: Display name
 *   - profile.bio: User biography
 *   - profile.avatarUrl: Profile picture URL
 *   - followersCount: Number of followers
 *   - followingCount: Number of followed users
 *   - createdAt: Account creation timestamp
 * - Fields NOT included:
 *   - AuthEntity (passwords, oauth IDs never exposed)
 *   - Sensitive account info
 *
 * Frontend Integration:
 * 1. Receive AuthResponse with token
 * 2. Store token for future requests (via cookie or localStorage)
 * 3. Use token in Authorization header for protected routes
 * 4. Display user.profile.name, user.username, user.profile.avatarUrl
 * 5. Track authState.followersCount, authState.followingCount
 *
 * Session Management:
 * - Stateless: No server-side session storage (JWT design)
 * - Token-based: Credentials embedded in token (not database lookup)
 * - Expiration: Frontend must handle 401 responses (re-login required)
 * - Refresh: No refresh token mechanism (implement if needed)
 *
 * Error Handling:
 * - Token generation failure: Rare, throws exception (500 Internal Error)
 * - User not found after creation: Race condition, should not occur
 * - Encoding issues: JWT encoding failure (500 error)
 *
 * @see UserEntity
 * @see JwtService
 * @see AuthController#signup(SignupRequest)
 * @see AuthController#login(LoginRequest)
 * @see AuthController#googleLogin(GoogleLoginRequest)
 * @since 1.0.0
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    /**
     * Authenticated user details (full UserEntity profile).
     *
     * Contents:
     * - All public profile information
     * - Never includes AuthEntity or sensitive credentials
     *
     * Fields:
     * - id: MongoDB document ID (unique user identifier)
     * - username: Unique username (3-20 chars, alphanumeric+underscore/dash/dot)
     * - email: Email address (used for login and password recovery)
     * - profile: Complex object with nested fields
     *   - name: Full name (from signup or Google profile)
     *   - bio: User biography (empty for new signups)
     *   - avatarUrl: URL to profile picture (empty for new signups, from Google for Google users)
     * - followersCount: Long (number of followers, initialized to 0)
     * - followingCount: Long (number of followed users, initialized to 0)
     * - createdAt: Instant (account creation timestamp)
     *
     * Usage:
     * - Display user profile information
     * - Update Redux/Vuex auth state
     * - Show username in navbar
     * - Initialize follower counts for UI
     * - Store in localStorage for session persistence
     *
     * Security:
     * - Public data only (safe to expose to client)
     * - Never includes passwords, OTPs, or OAuth IDs
     * - Email included (consider hiding in future for privacy)
     *
     * @see UserEntity
     */
    private UserEntity user;

    /**
     * JWT authentication token for accessing protected resources.
     *
     * Token Details:
     * - Format: JSON Web Token (JWT)
     * - Structure: header.payload.signature (three base64 parts)
     * - Algorithm: HS256 (HMAC with SHA-256)
     * - Signing Key: Application secret (symmetrical encryption)
     *
     * Payload Claims:
     * - userId: User's MongoDB ID (extracted for protected route authorization)
     * - sub (subject): Username (informational, not extracted)
     * - iat (issued at): Timestamp when token was created
     * - exp (expiration): Timestamp when token expires (typically +24 hours)
     * - Additional custom claims may be included
     *
     * Validity:
     * - Duration: 24 hours from issuance (JwtService configuration)
     * - After expiration: Token becomes invalid, requires new login
     * - No refresh mechanism: Full re-authentication required
     *
     * Storage Options:
     * 1. **Cookie (Recommended)**
     *    - Automatically sent with each request
     *    - HttpOnly flag prevents JavaScript access (XSS protection)
     *    - Secure flag ensures HTTPS only (MITM prevention)
     *    - Path: "/" (accessible to all routes)
     *    - MaxAge: 7 days (604800 seconds)
     *    - Easier to implement CSRF protection
     *
     * 2. **localStorage (Alternative)**
     *    - Manually include in Authorization header
     *    - Vulnerable to XSS attacks (JavaScript can access)
     *    - Required for cross-origin requests
     *    - Persists across tab/window closes
     *
     * Usage:
     * ```
     * Request Header: Authorization: Bearer [token]
     * OR
     * Cookie: auth_token=[token]
     * ```
     *
     * Extraction:
     * - Server extracts userId via JwtService.extractUserId(token)
     * - Stored in SecurityContextHolder for current request
     * - Used in @PathVariable or service methods for authorization
     *
     * Security Considerations:
     * - Never expose token in logs or error messages
     * - Token is stateless (cannot be revoked, must wait for expiration)
     * - Authentication: Proves user identity (logged in)
     * - Token theft = account access (protect with HTTPS, HttpOnly)
     * - Session fixation: Not vulnerable (client sends token, not used as ID)
     *
     * Logout:
     * - Clear cookie: POST /v1/auth/logout
     * - Remove from localStorage (if using that method)
     * - No server-side revocation (token remains valid until exp time)
     * - Recommendation: Implement token blacklist for immediate logout
     *
     * Example Token (decoded):
     * ```json
     * Header:
     * {
     *   "alg": "HS256",
     *   "typ": "JWT"
     * }
     *
     * Payload:
     * {
     *   "userId": "507f1f77bcf86cd799439011",
     *   "sub": "john_doe123",
     *   "iat": 1705328600,
     *   "exp": 1705415000
     * }
     *
     * Signature:
     * HMACSHA256(
     *   base64UrlEncode(header) + "." + base64UrlEncode(payload),
     *   "application-secret-key"
     * )
     * ```
     *
     * @see JwtService#generateToken(String)
     * @see JwtService#extractUserId(String)
     */
    private String token;
}