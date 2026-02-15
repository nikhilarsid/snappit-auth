package com.snapitt.backend_service.modules.auth.controller;

import com.snapitt.backend_service.modules.auth.dto.request.*;
import com.snapitt.backend_service.modules.auth.dto.request.*;
import com.snapitt.backend_service.modules.auth.dto.response.AuthResponse;
import com.snapitt.backend_service.modules.auth.service.AuthService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/signup")
    public ResponseEntity<UserEntity> signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        AuthResponse result = authService.signup(request);
        setCookie(response, result.getToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.status(HttpStatus.CREATED).body(result.getUser());
    }

    @PostMapping("/login")
    public ResponseEntity<UserEntity> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse result = authService.login(request);
        setCookie(response, result.getToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.ok(result.getUser());
    }

    @PostMapping("/google")
    public ResponseEntity<UserEntity> googleLogin(@Valid @RequestBody GoogleLoginRequest request, HttpServletResponse response) {
        AuthResponse result = authService.googleLogin(request);
        setCookie(response, result.getToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.ok(result.getUser());
    }

    @PostMapping("/forgot-password/init")
    public ResponseEntity<Void> initForgotPassword(@Valid @RequestBody ForgotPasswordInitRequest request) {
        authService.initForgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password/verify")
    public ResponseEntity<Void> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        setCookie(response, null, 0);
        return ResponseEntity.ok().build();
    }

    private void setCookie(HttpServletResponse response, String token, int maxAge) {
        String value = token == null ? "" : token;
        try {
            Cookie cookie = new Cookie("token", value);
            cookie.setHttpOnly(true);
            cookie.setSecure(true); // Always true for production
            cookie.setPath("/");
            cookie.setMaxAge(maxAge);
            response.addCookie(cookie);
        } catch (IllegalArgumentException iae) {
            log.warn("Failed to set cookie: {}", iae.getMessage());
            throw new com.snapitt.backend_service.modules.auth.common.exception.AuthException("Failed to set cookie", "COOKIE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}