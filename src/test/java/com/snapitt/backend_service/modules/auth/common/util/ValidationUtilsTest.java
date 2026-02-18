package com.snapitt.backend_service.modules.auth.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValidationUtils Unit Tests")
class ValidationUtilsTest {

    @Nested
    @DisplayName("isValidPassword")
    class PasswordValidation {

        @ParameterizedTest
        @ValueSource(strings = {"StrongP@ss1", "Abcdef1@xyz", "P@ssw0rd!", "Test#1234Ab"})
        @DisplayName("should accept valid passwords")
        void validPasswords(String password) {
            assertThat(ValidationUtils.isValidPassword(password)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"weak", "12345678", "abcdefgh", "ABCDEFGH", "Abcdefgh", "Abc1defgh", "Ab1@", "A b1@efgh"})
        @DisplayName("should reject invalid passwords")
        void invalidPasswords(String password) {
            assertThat(ValidationUtils.isValidPassword(password)).isFalse();
        }

        @Test
        @DisplayName("should reject null password")
        void nullPassword() {
            assertThat(ValidationUtils.isValidPassword(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidUsername")
    class UsernameValidation {

        @ParameterizedTest
        @ValueSource(strings = {"testuser", "user_name", "user.name", "abc", "a1234567890123456789"})
        @DisplayName("should accept valid usernames")
        void validUsernames(String username) {
            assertThat(ValidationUtils.isValidUsername(username)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ab", "user name", "user@name", "a_very_long_username_over_20_chars"})
        @DisplayName("should reject invalid usernames")
        void invalidUsernames(String username) {
            assertThat(ValidationUtils.isValidUsername(username)).isFalse();
        }

        @Test
        @DisplayName("should reject null username")
        void nullUsername() {
            assertThat(ValidationUtils.isValidUsername(null)).isFalse();
        }
    }
}
