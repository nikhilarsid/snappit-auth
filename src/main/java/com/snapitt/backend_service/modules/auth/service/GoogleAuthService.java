package com.snapitt.backend_service.modules.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * GoogleAuthService - Google OAuth2 Token Verification Service
 *
 * Verifies Google ID tokens received from client-side authentication flow.
 * Uses Google's official libraries for signature validation and claim extraction.
 *
 * Integration:
 * - Client obtains Google ID token via Google Sign-In SDK (client-side)
 * - Client sends ID token to server in GoogleLoginRequest
 * - GoogleAuthService verifies token signature and extracts payload
 * - AuthService uses verified payload to create/link user account
 *
 * Token Details:
 * - JWT format with Google's public keys for signature verification
 * - Includes user claims: email, sub (user ID), name, picture
 * - Signed by Google's OAuth2 OIDC issuer (accounts.google.com)
 * - Includes audience check to prevent token misuse across apps
 *
 * Dependencies:
 * - Google API Client Library (com.google.api-client)
 * - GsonFactory for JSON serialization
 * - NetHttpTransport for HTTPS verification
 *
 * Configuration:
 * - Google Client ID from spring.security.oauth2.client.registration.google.client-id
 * - Must match client-side Google app configuration
 *
 * @see GoogleIdTokenVerifier
 * @see GoogleIdToken.Payload
 * @since 1.0.0
 */
@Service
public class GoogleAuthService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    /**
     * Verify and extract claims from Google ID token.
     *
     * Token Verification Steps:
     * 1. Create GoogleIdTokenVerifier with:
     *    - NetHttpTransport for HTTP operations (downloads Google public keys)
     *    - GsonFactory for JSON parsing
     *    - Audience check: ensures token is for this application (clientId)
     * 2. Call verifier.verify(idTokenString) which:
     *    - Downloads Google's current public keys (cached)
     *    - Validates JWT signature using appropriate key
     *    - Checks expiration time (iat + expiry)
     *    - Verifies audience matches clientId
     * 3. If verification returns null, token is invalid
     * 4. If exception thrown during verification, catch and rethrow as AuthException
     * 5. Extract and return token payload (claims: email, sub, name, picture, etc.)
     *
     * Response Payload Contains:
     * - email: User's Google email address
     * - sub: Unique Google user ID (string)
     * - name: Full name from Google account (optional)
     * - picture: URL to profile picture (optional)
     * - aud: Audience claim (verified matches clientId)
     * - iat: Issued at timestamp (verified not expired)
     * - exp: Expiration timestamp (verified in future)
     * - issuer: https://accounts.google.com or https://oauth.google.com
     *
     * Security Features:
     * - Signature verification: Prevents token forgery
     * - Audience check: Ensures token is for this app, not leaked to another
     * - Expiration check: Tokens valid for ~1 hour from issuance
     * - Public key caching: Efficient verification with automatic key rotation
     * - Network validation: Downloads current keys from Google's JWKS endpoint
     *
     * Error Handling:
     * - Invalid signature: null returned, caught and throw INVALID_GOOGLE_TOKEN
     * - Audience mismatch: null returned (app receiving token for different app)
     * - Expiration: null returned (token too old)
     * - Network error: Exception thrown, caught and throw GOOGLE_AUTH_FAILED
     *
     * @param idTokenString JWT token string from Google (base64 encoded, 3 dot-separated parts)
     * @return GoogleIdToken.Payload containing verified user claims (email, sub, name, picture)
     * @throws AuthException if token is invalid (401) or verification fails (401)
     *         - INVALID_GOOGLE_TOKEN: Signature/audience/expiration mismatch
     *         - GOOGLE_AUTH_FAILED: Network, parsing, or unexpected error
     *
     * @see GoogleIdTokenVerifier#verify(String)
     * @see GoogleIdToken.Payload
     */
    public GoogleIdToken.Payload verifyToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new AuthException("Invalid Google Token", "INVALID_GOOGLE_TOKEN", HttpStatus.UNAUTHORIZED);
            }

            return idToken.getPayload();
        } catch (Exception e) {
            throw new AuthException("Google Authentication Failed", "GOOGLE_AUTH_FAILED", HttpStatus.UNAUTHORIZED);
        }
    }
}