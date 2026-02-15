package com.snapitt.backend_service.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * GoogleLoginRequest - Google OAuth2 Login Request DTO
 *
 * HTTP Endpoint: POST /v1/auth/google
 * Content-Type: application/json
 *
 * Request body for third-party Google OAuth2 authentication.
 * ID token obtained from Google Sign-In SDK (client-side).
 *
 * Client-Side Flow:
 * 1. User clicks "Sign in with Google" on frontend
 * 2. Google Sign-In SDK redirects to Google login page
 * 3. User authenticates with Google credentials
 * 4. SDK returns JWT ID token (valid for ~1 hour)
 * 5. Frontend sends ID token to backend via this request
 *
 * Server-Side Flow:
 * 1. Verify Google ID token signature via GoogleAuthService
 * 2. Check audience matches app's Google Client ID
 * 3. Verify token not expired (iat + expiry within valid window)
 * 4. Extract user claims: email, sub, name, picture
 * 5. Find or create user account (auto-linking by email)
 * 6. Generate server JWT token
 * 7. Return 200 OK with UserEntity and token
 *
 * Token Details:
 * - Format: JWT (3 dot-separated base64 parts)
 * - Signed: With Google's private keys (verified via public keys)
 * - Expires: ~1 hour from issuance
 * - Claims: email, sub (user ID), name, picture, aud, iss, exp, iat
 * - Single-use: Token not reified after initial verification
 *
 * Account Creation/Linking:
 * - First Google login: Creates new UserEntity with email + auto-generated username
 * - Subsequent login: Links to existing UserEntity by email
 * - Account linking: Automatic, no user approval required
 * - Security: GoogleSub (user ID) is globally unique within Google ecosystem
 *
 * Example Request:
 * ```json
 * {
 *   "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ.eyJhdWQiOiI4NzY1NDMyMTA5My1hYmNkZWYuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTIzODk1MzUwNzg2NTQzMjE5NzYiLCJlbWFpbCI6ImpvaG5AZXhhbXBsZS5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IkpvaG4gRG9lIiwicGljdHVyZSI6Imh0dHBzOi8vY2RuLnBpY3R1cmUvamQxMjMiLCJpYXQiOjE3MDEwMDAwMDB9.signature"
 * }
 * ```
 *
 * Success Response:
 * - Status: 200 OK
 * - Body: AuthResponse containing UserEntity and JWT token
 * - Cookie: auth_token with JWT (HttpOnly, Secure)
 *
 * Error Responses:
 * - 401 Unauthorized: Invalid token (signature/audience/expiration mismatch)
 *
 * Security Considerations:
 * - Token verification happens server-side (prevents client tampering)
 * - Audience check ensures token is for this app (prevents token reuse)
 * - Signature verification prevents forged tokens
 * - Email serves as account identifier (immutable within Google & this platform)
 * - GoogleSub stored for audit and duplicate detection
 *
 * Future Enhancements:
 * - Disconnect Google account option (if user has password)
 * - Multiple auth methods for same user (existing feature)
 * - Email verification enforcement for sensitive operations
 * - Risk assessment (new device, new location) and additional verification
 *
 * @see GoogleAuthService#verifyToken(String)
 * @see AuthService#googleLogin(GoogleLoginRequest)
 * @see AuthController#googleLogin(GoogleLoginRequest)
 * @since 1.0.0
 */
@Data
public class GoogleLoginRequest {
    /**
     * Google ID token (JWT format).
     *
     * Token Acquisition:
     * - Obtained from Google Sign-In SDK on frontend
     * - SDK handles OAuth2 redirect flow and token exchange
     * - Valid for ~3600 seconds (1 hour) from issuance
     *
     * Token Structure:
     * - Type: JWT (JSON Web Token)
     * - Parts: 3 base64-encoded parts separated by dots
     *   1. Header: {"alg": "RS256", "kid": "..."}
     *   2. Payload: Claims (email, sub, aud, exp, iat, etc.)
     *   3. Signature: Cryptographic signature with Google's private key
     * - Algorithm: RS256 (RSA with SHA-256)
     * - Size: Typically 1000-2000 bytes (varies with claims)
     *
     * Token Claims (Payload):
     * - "aud": Audience (Google Client ID, must match app's registered ID)
     * - "sub": Subject (Google user ID, 18-21 digit string, globally unique)
     * - "email": User's Google email address
     * - "email_verified": Boolean (always true for verified Google emails)
     * - "name": User's full name from Google profile
     * - "picture": URL to user's profile picture
     * - "iat": Issued at (timestamp, seconds since epoch)
     * - "exp": Expiration (timestamp, 1 hour after iat)
     * - "iss": Issuer ("https://accounts.google.com" or "https://oauth.google.com")
     *
     * Validation Process:
     * 1. Decode JWT (base64 decode without verification)
     * 2. Download Google's current public keys (JWKS endpoint, cached)
     * 3. Verify signature with appropriate key (identified by "kid" in header)
     * 4. Check expiration (current time < exp timestamp)
     * 5. Check audience (aud == app's Google Client ID)
     * 6. Extract claims for account creation/linking
     *
     * Storage:
     * - GoogleSub persisted in AuthEntity.googleSub (unique constraint)
     * - Email used to link to existing accounts
     * - Token itself not stored (stateless verification)
     *
     * Expiration Handling:
     * - Expired tokens rejected with 401 Unauthorized
     * - Client must obtain new token via Google Sign-In SDK refresh
     * - Typical user session: Fresh token obtained per login/refresh
     *
     * @see GoogleAuthService#verifyToken(String)
     * @see com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload
     */
    @NotBlank(message = "ID Token is required")
    private String idToken;
}