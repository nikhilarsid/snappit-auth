package com.snapitt.backend_service.modules.auth.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * AuthEntity - User Authentication Credentials Model
 *
 * Stores authentication method and credentials for each user.
 * Decouples identity (UserEntity) from authentication (AuthEntity).
 *
 * Design:
 * - One UserEntity → One or More AuthEntity
 * - Allows users to authenticate via multiple methods (LOCAL + GOOGLE)
 * - Supports future auth types (SAML, passwordless, etc.)
 * - Each auth method tracked independently in separate document
 *
 * MongoDB Collection: "auth"
 * Indexes:
 * - user_type_idx (compound, unique): {userId: 1, type: 1}
 *   Enforces one auth per user per type (prevents duplicate credentials)
 * - googleSub (unique, sparse): Indexes Google user ID for fast lookup
 *   Sparse allows null values for non-Google auth methods
 * - userId (regular): Indexes user ID for query efficiency
 *
 * Authentication Types:
 * - LOCAL: Username/email + bcrypt password hash
 * - GOOGLE: Google OAuth2 with sub (Google user ID)
 *
 * Constraints:
 * - userId is required and indexed (foreign key to UserEntity)
 * - type is required (determines which fields are populated)
 * - passwordHash: Only populated for LOCAL auth
 * - googleSub: Only populated for GOOGLE auth, must be unique across platform
 *
 * Related Entities:
 * - UserEntity: User profile, linked via userId
 * - OtpEntity: Temporary credentials for password reset
 *
 * @see UserEntity
 * @see OtpEntity
 * @see AuthType
 * @since 1.0.0
 */
@Data
@Builder
@Document(collection = "auth")
@CompoundIndex(name = "user_type_idx", def = "{'userId': 1, 'type': 1}", unique = true)
public class AuthEntity {
    /**
     * Unique primary key (MongoDB ObjectId).
     * Format: 24-character hexadecimal string.
     */
    @Id
    private String id;

    /**
     * User ID (foreign key to UserEntity).
     * Links this auth record to a UserEntity.
     * Indexed for efficient lookup during login.
     * Uniqueness enforced per auth type via compound index.
     *
     * @see UserEntity#id
     */
    @Indexed
    private String userId;

    /**
     * Authentication method/type.
     * Determines which credential fields are populated:
     * - LOCAL: Uses passwordHash field (bcrypt encoded)
     * - GOOGLE: Uses googleSub field (Google user ID)
     *
     * Required field, cannot be null.
     * Combined with userId ensures one auth per type.
     *
     * @see AuthType
     */
    private AuthType type; // ENUM: LOCAL or GOOGLE

    /**
     * Bcrypt-encoded password hash (LOCAL auth only).
     * Generated via PasswordEncoder.encode(password).
     * Used for password verification during login.
     *
     * Properties:
     * - Length: 60 characters (bcrypt standard)
     * - Format: $2a$12$... or $2b$12$... (version + cost + salt + hash)
     * - Security: Includes salt and cost factor for brute-force resistance
     * - Irreversible: Cannot decrypt to original password
     *
     * Per-login verification: PasswordEncoder.matches(provided, stored)
     *
     * Required for AuthType.LOCAL, nullable for AuthType.GOOGLE.
     * Not exposed to API responses (never serialized to client).
     *
     * @see org.springframework.security.crypto.password.PasswordEncoder
     */
    private String passwordHash;

    /**
     * Google user ID (GOOGLE auth only).
     * Provided by Google in ID token payload ["sub" claim].
     * Unique across entire Google ecosystem.
     * Uniqueness enforced at database level with sparse index.
     *
     * Properties:
     * - Format: Decimal string, typically 18-20 digits
     * - Example: "118012345678901234567"
     * - Permanence: Never changes for same Google account
     * - Scope: Unique per Google user (different apps see same sub)
     *
     * Used for:
     * - Account linking: Find existing user by googleSub
     * - Duplicate prevention: Unique constraint prevents multiple accounts
     * - Security: Cannot forge Google identity without valid token
     *
     * Required for AuthType.GOOGLE, nullable for AuthType.LOCAL.
     * Index is sparse to allow nulls (LOCAL auth records don't need this field).
     * Not exposed to API responses (sensitive identifiers).
     *
     * @see com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload#getSubject()
     */
    @Indexed(unique = true, sparse = true)
    private String googleSub;

    /**
     * Authentication Type Enumeration.
     *
     * LOCAL: Local username/email and password authentication
     *   - User registers with email/username + password
     *   - Password is bcrypt hashed in passwordHash field
     *   - Login validates password with PasswordEncoder.matches()
     *
     * GOOGLE: Google OAuth2 third-party authentication
     *   - User authenticates via Google Sign-In SDK
     *   - Server receives JWT ID token with user claims
     *   - Google user ID (sub) stored in googleSub field
     *   - Account automatically created or linked to existing user
     *
     * @see AuthService#signup(SignupRequest)
     * @see AuthService#login(LoginRequest)
     * @see AuthService#googleLogin(GoogleLoginRequest)
     */
    public enum AuthType {
        LOCAL, GOOGLE
    }
}