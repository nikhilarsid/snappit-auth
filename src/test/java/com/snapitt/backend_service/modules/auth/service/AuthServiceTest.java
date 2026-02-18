package com.snapitt.backend_service.modules.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.dto.request.*;
import com.snapitt.backend_service.modules.auth.dto.response.AuthResponse;
import com.snapitt.backend_service.modules.auth.model.AuthEntity;
import com.snapitt.backend_service.modules.auth.model.AuthEntity.AuthType;
import com.snapitt.backend_service.modules.auth.model.OtpEntity;
import com.snapitt.backend_service.modules.auth.repository.AuthRepository;
import com.snapitt.backend_service.modules.auth.repository.OtpRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import com.snapitt.backend_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthRepository authRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GoogleAuthService googleAuthService;

    @InjectMocks
    private AuthService authService;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id("user-1")
                .username("testuser")
                .email("test@example.com")
                .profile(UserEntity.Profile.builder().name("Test User").bio("").avatarUrl("").build())
                .followersCount(0L)
                .followingCount(0L)
                .build();
    }

    @Nested
    @DisplayName("signup")
    class SignupTests {

        private SignupRequest validRequest() {
            SignupRequest req = new SignupRequest();
            req.setName("Test User");
            req.setUsername("testuser");
            req.setEmail("test@example.com");
            req.setPassword("StrongP@ss1");
            return req;
        }

        @Test
        @DisplayName("should create user and return token on valid signup")
        void signup_success() {
            SignupRequest request = validRequest();
            when(userRepository.existsByUsername("testuser")).thenReturn(false);
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);
            when(authRepository.save(any(AuthEntity.class))).thenReturn(AuthEntity.builder().build());
            when(passwordEncoder.encode("StrongP@ss1")).thenReturn("hashed");
            when(jwtService.generateToken("user-1")).thenReturn("jwt-token");

            AuthResponse response = authService.signup(request);

            assertThat(response).isNotNull();
            assertThat(response.getUser().getUsername()).isEqualTo("testuser");
            assertThat(response.getToken()).isEqualTo("jwt-token");
            verify(userRepository).save(any(UserEntity.class));
            verify(authRepository).save(any(AuthEntity.class));
        }

        @Test
        @DisplayName("should save auth entity with LOCAL type and hashed password")
        void signup_savesCorrectAuthEntity() {
            SignupRequest request = validRequest();
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.save(any())).thenReturn(testUser);
            when(passwordEncoder.encode("StrongP@ss1")).thenReturn("hashed-pw");
            when(jwtService.generateToken(anyString())).thenReturn("tok");
            when(authRepository.save(any(AuthEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.signup(request);

            ArgumentCaptor<AuthEntity> captor = ArgumentCaptor.forClass(AuthEntity.class);
            verify(authRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(AuthType.LOCAL);
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-pw");
            assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("should throw WEAK_PASSWORD for weak password")
        void signup_weakPassword() {
            SignupRequest request = validRequest();
            request.setPassword("weak");

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> {
                        assertThat(((AuthException) ex).getErrorCode()).isEqualTo("WEAK_PASSWORD");
                        assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw INVALID_USERNAME_FORMAT for short username")
        void signup_invalidUsername() {
            SignupRequest request = validRequest();
            request.setUsername("ab");

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("INVALID_USERNAME_FORMAT"));
        }

        @Test
        @DisplayName("should throw USERNAME_ALREADY_EXISTS for duplicate username")
        void signup_duplicateUsername() {
            SignupRequest request = validRequest();
            when(userRepository.existsByUsername("testuser")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> {
                        assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USERNAME_ALREADY_EXISTS");
                        assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    });
        }

        @Test
        @DisplayName("should throw EMAIL_ALREADY_EXISTS for duplicate email")
        void signup_duplicateEmail() {
            SignupRequest request = validRequest();
            when(userRepository.existsByUsername("testuser")).thenReturn(false);
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS"));
        }
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        private LoginRequest loginReq(String usernameOrEmail, String password) {
            LoginRequest req = new LoginRequest();
            req.setUsernameOrEmail(usernameOrEmail);
            req.setPassword(password);
            return req;
        }

        @Test
        @DisplayName("should login with username")
        void login_byUsername() {
            AuthEntity auth = AuthEntity.builder().userId("user-1").type(AuthType.LOCAL).passwordHash("hashed").build();
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.LOCAL)).thenReturn(Optional.of(auth));
            when(passwordEncoder.matches("StrongP@ss1", "hashed")).thenReturn(true);
            when(jwtService.generateToken("user-1")).thenReturn("jwt-token");

            AuthResponse response = authService.login(loginReq("testuser", "StrongP@ss1"));

            assertThat(response.getUser().getUsername()).isEqualTo("testuser");
            assertThat(response.getToken()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("should login with email")
        void login_byEmail() {
            AuthEntity auth = AuthEntity.builder().userId("user-1").type(AuthType.LOCAL).passwordHash("hashed").build();
            when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.LOCAL)).thenReturn(Optional.of(auth));
            when(passwordEncoder.matches("StrongP@ss1", "hashed")).thenReturn(true);
            when(jwtService.generateToken("user-1")).thenReturn("jwt-token");

            AuthResponse response = authService.login(loginReq("test@example.com", "StrongP@ss1"));
            assertThat(response.getToken()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("should throw INVALID_CREDENTIALS when user not found")
        void login_userNotFound() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginReq("nobody", "any")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> {
                        assertThat(((AuthException) ex).getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                        assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    });
        }

        @Test
        @DisplayName("should throw GOOGLE_AUTH_REQUIRED when no local auth")
        void login_googleOnly() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.LOCAL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginReq("testuser", "any")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("GOOGLE_AUTH_REQUIRED"));
        }

        @Test
        @DisplayName("should throw INVALID_CREDENTIALS on wrong password")
        void login_wrongPassword() {
            AuthEntity auth = AuthEntity.builder().userId("user-1").type(AuthType.LOCAL).passwordHash("hashed").build();
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.LOCAL)).thenReturn(Optional.of(auth));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginReq("testuser", "wrong")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("INVALID_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("googleLogin")
    class GoogleLoginTests {

        @Test
        @DisplayName("should login existing user via Google")
        void googleLogin_existingUser() {
            GoogleLoginRequest request = new GoogleLoginRequest();
            request.setIdToken("valid-token");

            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setEmail("test@example.com");
            payload.setSubject("google-sub");
            payload.set("name", "Test User");
            payload.set("picture", "https://avatar.url");

            when(googleAuthService.verifyToken("valid-token")).thenReturn(payload);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.GOOGLE)).thenReturn(Optional.of(AuthEntity.builder().build()));
            when(jwtService.generateToken("user-1")).thenReturn("jwt-token");

            AuthResponse response = authService.googleLogin(request);

            assertThat(response.getUser().getUsername()).isEqualTo("testuser");
            assertThat(response.getToken()).isEqualTo("jwt-token");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create new user for first-time Google login")
        void googleLogin_newUser() {
            GoogleLoginRequest request = new GoogleLoginRequest();
            request.setIdToken("valid-token");

            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setEmail("new@example.com");
            payload.setSubject("google-sub-456");
            payload.set("name", "New User");
            payload.set("picture", "https://pic.url");

            UserEntity newUser = UserEntity.builder().id("user-2").username("new").email("new@example.com").build();

            when(googleAuthService.verifyToken("valid-token")).thenReturn(payload);
            when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any())).thenReturn(newUser);
            when(authRepository.save(any())).thenReturn(AuthEntity.builder().build());
            when(jwtService.generateToken("user-2")).thenReturn("jwt-2");

            AuthResponse response = authService.googleLogin(request);

            assertThat(response.getToken()).isEqualTo("jwt-2");
            verify(userRepository).save(any(UserEntity.class));
            verify(authRepository).save(argThat(auth -> auth.getType() == AuthType.GOOGLE));
        }

        @Test
        @DisplayName("should add Google auth record for existing user without one")
        void googleLogin_existingUser_noGoogleAuth() {
            GoogleLoginRequest request = new GoogleLoginRequest();
            request.setIdToken("valid-token");

            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setEmail("test@example.com");
            payload.setSubject("google-sub");
            payload.set("name", "Test User");
            payload.set("picture", "https://avatar.url");

            when(googleAuthService.verifyToken("valid-token")).thenReturn(payload);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.GOOGLE)).thenReturn(Optional.empty());
            when(authRepository.save(any())).thenReturn(AuthEntity.builder().build());
            when(jwtService.generateToken("user-1")).thenReturn("jwt");

            authService.googleLogin(request);

            verify(authRepository).save(argThat(auth -> auth.getType() == AuthType.GOOGLE && "google-sub".equals(auth.getGoogleSub())));
        }
    }

    @Nested
    @DisplayName("initForgotPassword")
    class ForgotPasswordTests {

        @Test
        @DisplayName("should create OTP for valid email")
        void initForgotPassword_success() {
            ForgotPasswordInitRequest req = new ForgotPasswordInitRequest();
            req.setEmail("test@example.com");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.initForgotPassword(req);

            ArgumentCaptor<OtpEntity> captor = ArgumentCaptor.forClass(OtpEntity.class);
            verify(otpRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
            assertThat(captor.getValue().getCode()).hasSize(6);
            assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when email not found")
        void initForgotPassword_emailNotFound() {
            ForgotPasswordInitRequest req = new ForgotPasswordInitRequest();
            req.setEmail("unknown@example.com");
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.initForgotPassword(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("verifyOtp")
    class VerifyOtpTests {

        @Test
        @DisplayName("should verify valid OTP successfully")
        void verifyOtp_success() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("test@example.com");
            req.setCode("123456");

            OtpEntity otp = OtpEntity.builder().email("test@example.com").code("123456").expiresAt(Instant.now().plusSeconds(300)).build();
            when(otpRepository.findTopByEmailOrderByExpiresAtDesc("test@example.com")).thenReturn(Optional.of(otp));

            assertThatCode(() -> authService.verifyOtp(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw OTP_EXPIRED when OTP expired")
        void verifyOtp_expired() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("test@example.com");
            req.setCode("123456");

            OtpEntity otp = OtpEntity.builder().email("test@example.com").code("123456").expiresAt(Instant.now().minusSeconds(60)).build();
            when(otpRepository.findTopByEmailOrderByExpiresAtDesc("test@example.com")).thenReturn(Optional.of(otp));

            assertThatThrownBy(() -> authService.verifyOtp(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("OTP_EXPIRED"));
        }

        @Test
        @DisplayName("should throw INVALID_OTP when code doesn't match")
        void verifyOtp_wrongCode() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("test@example.com");
            req.setCode("000000");

            OtpEntity otp = OtpEntity.builder().email("test@example.com").code("123456").expiresAt(Instant.now().plusSeconds(300)).build();
            when(otpRepository.findTopByEmailOrderByExpiresAtDesc("test@example.com")).thenReturn(Optional.of(otp));

            assertThatThrownBy(() -> authService.verifyOtp(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("INVALID_OTP"));
        }

        @Test
        @DisplayName("should throw INVALID_OTP when no OTP found")
        void verifyOtp_notFound() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("test@example.com");
            req.setCode("123456");

            when(otpRepository.findTopByEmailOrderByExpiresAtDesc("test@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyOtp(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("INVALID_OTP"));
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPasswordTests {

        @Test
        @DisplayName("should reset password for existing local auth")
        void resetPassword_existingLocalAuth() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("test@example.com");
            req.setCode("123456");
            req.setNewPassword("NewStr0ng@Pass");

            OtpEntity otp = OtpEntity.builder().id("otp-1").email("test@example.com").code("123456").expiresAt(Instant.now().plusSeconds(300)).build();
            AuthEntity auth = AuthEntity.builder().id("auth-1").userId("user-1").type(AuthType.LOCAL).passwordHash("old").build();

            when(otpRepository.findTopByEmailOrderByExpiresAtDesc("test@example.com")).thenReturn(Optional.of(otp));
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.LOCAL)).thenReturn(Optional.of(auth));
            when(passwordEncoder.encode("NewStr0ng@Pass")).thenReturn("new-hash");
            when(authRepository.save(any())).thenReturn(auth);

            authService.resetPassword(req);

            verify(authRepository).save(argThat(a -> "new-hash".equals(a.getPasswordHash())));
            verify(otpRepository).deleteById("otp-1");
        }

        @Test
        @DisplayName("should create local auth if only Google auth exists")
        void resetPassword_noLocalAuth() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("test@example.com");
            req.setCode("123456");
            req.setNewPassword("NewStr0ng@Pass");

            OtpEntity otp = OtpEntity.builder().id("otp-1").email("test@example.com").code("123456").expiresAt(Instant.now().plusSeconds(300)).build();

            when(otpRepository.findTopByEmailOrderByExpiresAtDesc("test@example.com")).thenReturn(Optional.of(otp));
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(authRepository.findByUserIdAndType("user-1", AuthType.LOCAL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode("NewStr0ng@Pass")).thenReturn("new-hash");
            when(authRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.resetPassword(req);

            ArgumentCaptor<AuthEntity> captor = ArgumentCaptor.forClass(AuthEntity.class);
            verify(authRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(AuthType.LOCAL);
        }

        @Test
        @DisplayName("should throw WEAK_PASSWORD for weak new password")
        void resetPassword_weakPassword() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("test@example.com");
            req.setCode("123456");
            req.setNewPassword("weak");

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("WEAK_PASSWORD"));
        }
    }
}
