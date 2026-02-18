package com.snapitt.backend_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // HS256 requires at least 256 bits (32 bytes); this base64 key is 44 chars ≈ 32 bytes
        ReflectionTestUtils.setField(jwtService, "secretKey", "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2g=");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hour
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateTokenTests {

        @Test
        @DisplayName("should generate a valid JWT token")
        void generateToken_success() {
            String token = jwtService.generateToken("user-123");

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        }

        @Test
        @DisplayName("should generate different tokens for different users")
        void generateToken_differentUsers() {
            String token1 = jwtService.generateToken("user-1");
            String token2 = jwtService.generateToken("user-2");

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("extractUserId")
    class ExtractUserIdTests {

        @Test
        @DisplayName("should extract the user ID from a valid token")
        void extractUserId_success() {
            String token = jwtService.generateToken("user-123");

            String userId = jwtService.extractUserId(token);

            assertThat(userId).isEqualTo("user-123");
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValidTests {

        @Test
        @DisplayName("should return true for valid token")
        void isTokenValid_true() {
            String token = jwtService.generateToken("user-123");

            boolean valid = jwtService.isTokenValid(token);

            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("should return false for tampered token")
        void isTokenValid_tampered() {
            String token = jwtService.generateToken("user-123");
            String tampered = token + "tampered";

            boolean valid = jwtService.isTokenValid(tampered);

            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should return false for garbage string")
        void isTokenValid_garbage() {
            boolean valid = jwtService.isTokenValid("not-a-jwt-token");

            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should return false for expired token")
        void isTokenValid_expired() {
            // Set expiration to 0 so token is immediately expired
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", 0L);
            String token = jwtService.generateToken("user-123");

            // Token should be expired immediately or within ms
            // Small timing window - give it a small sleep
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            boolean valid = jwtService.isTokenValid(token);

            assertThat(valid).isFalse();
        }
    }
}
