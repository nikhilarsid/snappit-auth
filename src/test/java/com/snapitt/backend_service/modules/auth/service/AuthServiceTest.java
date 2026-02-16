package com.snapitt.backend_service.modules.auth.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.dto.request.LoginRequest;
import com.snapitt.backend_service.modules.auth.dto.request.SignupRequest;
import com.snapitt.backend_service.modules.auth.dto.response.AuthResponse;
import com.snapitt.backend_service.modules.auth.model.AuthEntity;
import com.snapitt.backend_service.modules.auth.model.AuthEntity.AuthType;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import com.snapitt.backend_service.modules.auth.repository.AuthRepository;
import com.snapitt.backend_service.modules.auth.repository.OtpRepository;
import com.snapitt.backend_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthRepository authRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private GoogleAuthService googleAuthService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void signup_success_createsUserAndReturnsToken() {
        SignupRequest req = new SignupRequest();
        req.setUsername("tester");
        req.setEmail("test@example.com");
        req.setName("Test User");
        req.setPassword("StrongPass1!");

        when(userRepository.existsByUsername("tester")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);

        UserEntity saved = UserEntity.builder().id("uid123").username("tester").email("test@example.com").build();
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);

        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtService.generateToken("uid123")).thenReturn("tok-xyz");

        AuthResponse resp = authService.signup(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getToken()).isEqualTo("tok-xyz");
        assertThat(resp.getUser()).isEqualTo(saved);

        verify(authRepository, times(1)).save(any(AuthEntity.class));
    }

    @Test
    void signup_weakPassword_throwsAuthException() {
        SignupRequest req = new SignupRequest();
        req.setUsername("tester");
        req.setEmail("test@example.com");
        req.setName("Test User");
        req.setPassword("weak");

        AuthException ex = assertThrows(AuthException.class, () -> authService.signup(req));
        assertThat(ex.getErrorCode()).isEqualTo("WEAK_PASSWORD");
    }

    @Test
    void login_invalidPassword_throwsInvalidCredentials() {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("tester");
        req.setPassword("password");

        UserEntity user = UserEntity.builder().id("u1").username("tester").email("t@e").build();
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(authRepository.findByUserIdAndType("u1", AuthType.LOCAL)).thenReturn(Optional.of(AuthEntity.builder().passwordHash("hash").build()));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(req));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    }
}
