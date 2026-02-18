package com.snapitt.backend_service.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.auth.dto.request.*;
import com.snapitt.backend_service.modules.auth.dto.response.AuthResponse;
import com.snapitt.backend_service.modules.auth.service.AuthService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    @Mock private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserEntity createUser() {
        return UserEntity.builder()
                .id("user-1").username("testuser").email("test@email.com")
                .profile(UserEntity.Profile.builder().name("Test").build())
                .build();
    }

    @Nested
    @DisplayName("POST /v1/auth/signup")
    class SignupTests {

        @Test
        @DisplayName("should return 201 with user and set cookie")
        void signup_success() throws Exception {
            AuthResponse authResponse = new AuthResponse(createUser(), "jwt-token");

            when(authService.signup(any(SignupRequest.class))).thenReturn(authResponse);

            SignupRequest signupReq = new SignupRequest();
            signupReq.setName("Test");
            signupReq.setUsername("testuser");
            signupReq.setEmail("test@email.com");
            signupReq.setPassword("P@ssword1");

            mockMvc.perform(post("/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signupReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(cookie().exists("token"))
                    .andExpect(cookie().value("token", "jwt-token"));
        }

        @Test
        @DisplayName("should return error when signup fails")
        void signup_failure() throws Exception {
            when(authService.signup(any(SignupRequest.class)))
                    .thenThrow(new AuthException("Username already exists", "USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT));

            SignupRequest signupReq = new SignupRequest();
            signupReq.setName("Taken");
            signupReq.setUsername("taken");
            signupReq.setEmail("test@email.com");
            signupReq.setPassword("P@ssword1");

            mockMvc.perform(post("/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signupReq)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("USERNAME_ALREADY_EXISTS"));
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("should return 200 with user and set cookie")
        void login_success() throws Exception {
            AuthResponse authResponse = new AuthResponse(createUser(), "jwt-token");

            when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsernameOrEmail("testuser");
            loginReq.setPassword("P@ssword1");

            mockMvc.perform(post("/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(cookie().exists("token"));
        }

        @Test
        @DisplayName("should return 401 for wrong password")
        void login_wrongPassword() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AuthException("Invalid password", "INVALID_PASSWORD", HttpStatus.UNAUTHORIZED));

            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsernameOrEmail("testuser");
            loginReq.setPassword("wrong");

            mockMvc.perform(post("/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"));
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/google")
    class GoogleLoginTests {

        @Test
        @DisplayName("should return 200 with user")
        void googleLogin_success() throws Exception {
            AuthResponse authResponse = new AuthResponse(createUser(), "jwt-token");

            when(authService.googleLogin(any(GoogleLoginRequest.class))).thenReturn(authResponse);

            GoogleLoginRequest googleReq = new GoogleLoginRequest();
            googleReq.setIdToken("google-id-token");

            mockMvc.perform(post("/v1/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(googleReq)))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("token"));
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/forgot-password/*")
    class ForgotPasswordTests {

        @Test
        @DisplayName("should return 200 for init forgot password")
        void initForgotPassword_success() throws Exception {
            doNothing().when(authService).initForgotPassword(any(ForgotPasswordInitRequest.class));

            ForgotPasswordInitRequest fpReq = new ForgotPasswordInitRequest();
            fpReq.setEmail("test@email.com");

            mockMvc.perform(post("/v1/auth/forgot-password/init")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(fpReq)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 200 for verify OTP")
        void verifyOtp_success() throws Exception {
            doNothing().when(authService).verifyOtp(any(VerifyOtpRequest.class));

            VerifyOtpRequest verifyReq = new VerifyOtpRequest();
            verifyReq.setEmail("test@email.com");
            verifyReq.setCode("123456");

            mockMvc.perform(post("/v1/auth/forgot-password/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(verifyReq)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 200 for reset password")
        void resetPassword_success() throws Exception {
            doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

            ResetPasswordRequest resetReq = new ResetPasswordRequest();
            resetReq.setEmail("test@email.com");
            resetReq.setCode("123456");
            resetReq.setNewPassword("NewP@ss1");

            mockMvc.perform(post("/v1/auth/forgot-password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resetReq)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("should clear cookie and return 200")
        void logout_success() throws Exception {
            mockMvc.perform(post("/v1/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("token", 0));
        }
    }
}
