package com.snapitt.backend_service.modules.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.util.ValidationUtils;
import com.snapitt.backend_service.modules.auth.dto.request.*;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleAuthService googleAuthService;
    // private final EmailService emailService; // Uncomment when EmailService is ready

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (!ValidationUtils.isValidPassword(request.getPassword())) {
            throw new AuthException("Password is too weak", "WEAK_PASSWORD", HttpStatus.BAD_REQUEST);
        }
        if (!ValidationUtils.isValidUsername(request.getUsername())) {
            throw new AuthException("Invalid username format", "INVALID_USERNAME_FORMAT", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException("Username already in use", "USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email already registered", "EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT);
        }

        // ✅ FIXED: Safely building profile with empty strings instead of nulls
        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .profile(UserEntity.Profile.builder()
                        .name(request.getName())
                        .bio("")       // Safe default
                        .avatarUrl("") // Safe default
                        .build())
                .followersCount(0L)
                .followingCount(0L)
                .build();
        user = userRepository.save(user);

        AuthEntity auth = AuthEntity.builder()
                .userId(user.getId())
                .type(AuthType.LOCAL)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        authRepository.save(auth);

        String token = jwtService.generateToken(user.getId());
        return new AuthResponse(user, token);
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(request.getUsernameOrEmail()))
                .orElseThrow(() -> new AuthException("Incorrect credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED));

        AuthEntity auth = authRepository.findByUserIdAndType(user.getId(), AuthType.LOCAL)
                .orElseThrow(() -> new AuthException("This account uses Google login", "GOOGLE_AUTH_REQUIRED", HttpStatus.FORBIDDEN));

        if (!passwordEncoder.matches(request.getPassword(), auth.getPasswordHash())) {
            throw new AuthException("Incorrect credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(user.getId());
        return new AuthResponse(user, token);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleAuthService.verifyToken(request.getIdToken());

        String email = payload.getEmail();
        String sub = payload.getSubject();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        Optional<UserEntity> existingUser = userRepository.findByEmail(email);
        UserEntity user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Link Account if missing
            Optional<AuthEntity> googleAuth = authRepository.findByUserIdAndType(user.getId(), AuthType.GOOGLE);
            if (googleAuth.isEmpty()) {
                AuthEntity newAuth = AuthEntity.builder()
                        .userId(user.getId())
                        .type(AuthType.GOOGLE)
                        .googleSub(sub)
                        .build();
                authRepository.save(newAuth);
            }
        } else {
            // New User
            user = UserEntity.builder()
                    .email(email)
                    .username(email.split("@")[0] + new Random().nextInt(1000))
                    .profile(UserEntity.Profile.builder()
                            .name(name)
                            .avatarUrl(picture)
                            .bio("") // Safe default
                            .build())
                    .followersCount(0L)
                    .followingCount(0L)
                    .build();
            user = userRepository.save(user);

            AuthEntity auth = AuthEntity.builder()
                    .userId(user.getId())
                    .type(AuthType.GOOGLE)
                    .googleSub(sub)
                    .build();
            authRepository.save(auth);
        }

        String token = jwtService.generateToken(user.getId());
        return new AuthResponse(user, token);
    }

    public void initForgotPassword(ForgotPasswordInitRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

        String code = String.format("%06d", new Random().nextInt(999999));
        OtpEntity otp = OtpEntity.builder()
                .email(user.getEmail())
                .code(code)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        otpRepository.save(otp);
        
        // TODO: Uncomment this when you implement EmailService
        // emailService.sendOtp(user.getEmail(), code);
        
        // For development, print to console:
        System.out.println("OTP for " + user.getEmail() + ": " + code);
    }

    public void verifyOtp(VerifyOtpRequest request) {
        OtpEntity otp = otpRepository.findTopByEmailOrderByExpiresAtDesc(request.getEmail())
                .orElseThrow(() -> new AuthException("Invalid OTP", "INVALID_OTP", HttpStatus.BAD_REQUEST));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("OTP Expired", "OTP_EXPIRED", HttpStatus.BAD_REQUEST);
        }
        if (!otp.getCode().equals(request.getCode())) {
            throw new AuthException("Invalid OTP", "INVALID_OTP", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!ValidationUtils.isValidPassword(request.getNewPassword())) {
            throw new AuthException("Password is too weak", "WEAK_PASSWORD", HttpStatus.BAD_REQUEST);
        }

        // Create VerifyOtpRequest using setters
        VerifyOtpRequest verifyRequest = new VerifyOtpRequest();
        verifyRequest.setEmail(request.getEmail());
        verifyRequest.setCode(request.getCode());
        verifyOtp(verifyRequest);

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

        Optional<AuthEntity> localAuth = authRepository.findByUserIdAndType(user.getId(), AuthType.LOCAL);

        if (localAuth.isPresent()) {
            AuthEntity auth = localAuth.get();
            auth.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            authRepository.save(auth);
        } else {
            AuthEntity auth = AuthEntity.builder()
                    .userId(user.getId())
                    .type(AuthType.LOCAL)
                    .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                    .build();
            authRepository.save(auth);
        }

        // Clean up OTP
        var otp = otpRepository.findTopByEmailOrderByExpiresAtDesc(request.getEmail());
        otp.ifPresent(otpEntity -> otpRepository.deleteById(otpEntity.getId()));
    }
}