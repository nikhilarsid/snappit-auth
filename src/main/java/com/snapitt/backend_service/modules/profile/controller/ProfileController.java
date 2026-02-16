package com.snapitt.backend_service.modules.profile.controller;

import com.snapitt.backend_service.modules.profile.dto.request.UpdateProfileRequest;
import com.snapitt.backend_service.modules.profile.dto.response.ProfileResponse;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * ProfileController - Handles user profile operations
 *
 * GET /v1/profile/{username} - Get public profile
 *   - 400: INVALID_USERNAME - Invalid username format (validated via @Pattern on @PathVariable)
 *   - 404: USER_NOT_FOUND - User profile does not exist
 *   - 500: INTERNAL_SERVER_ERROR - Database or server error
 *
 * GET /v1/profile/my - Get own profile
 *   - 401: UNAUTHORIZED - Missing or invalid authentication token
 *   - 404: USER_NOT_FOUND - User not found
 *   - 500: INTERNAL_SERVER_ERROR - Database or server error
 *
 * PATCH /v1/profile - Update own profile
 *   - 400: NO_VALID_FIELDS - No updatable fields provided
 *   - 400: VALIDATION_ERROR - Field validation failed (size constraints from @Valid on UpdateProfileRequest)
 *   - 401: UNAUTHORIZED - Missing or invalid authentication token
 *   - 404: USER_NOT_FOUND - User not found (edge case)
 *   - 500: INTERNAL_SERVER_ERROR - Database or server error
 *
 * Restricted fields (cannot be updated): username, email, followersCount, followingCount, createdAt
 */
@RestController
@RequestMapping("/v1/profile")
@RequiredArgsConstructor
@Validated
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Get public profile by username
     * Username format validated via @Pattern annotation on path variable
     *
     * Exceptions:
     * - ConstraintViolationException (INVALID_USERNAME, 400) - Invalid username format
     * - AuthException (USER_NOT_FOUND, 404) - User not found
     * - Exception caught by GlobalExceptionHandler for unexpected errors
     */
    @GetMapping("/{username}")
    public ResponseEntity<ProfileResponse> getProfile(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String viewerId = null;
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            viewerId = ((UserPrincipal) auth.getPrincipal()).getUser().getId();
        }

        ProfileResponse profile = profileService.getProfile(viewerId, username);
        return ResponseEntity.ok(profile);
    }

    /**
     * Get own profile by user ID from JWT
     *
     * Exceptions:
     * - AuthException (UNAUTHORIZED, 401) - Missing or invalid authentication
     * - AuthException (USER_NOT_FOUND, 404) - User not found
     * - Exception caught by GlobalExceptionHandler for unexpected errors
     */
    @GetMapping("/my")
    public ResponseEntity<ProfileResponse> getMyProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new com.snapitt.backend_service.modules.auth.common.exception.AuthException(
                    "Authentication required",
                    "UNAUTHORIZED",
                    org.springframework.http.HttpStatus.UNAUTHORIZED
            );
        }

        String userId = ((UserPrincipal) auth.getPrincipal()).getUser().getId();
        ProfileResponse profile = profileService.getMyProfile(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * Update own profile
     * Request body fields validated via @Valid annotation on UpdateProfileRequest DTO
     *
     * Exceptions:
     * - AuthException (UNAUTHORIZED, 401) - Missing or invalid authentication
     * - MethodArgumentNotValidException (VALIDATION_ERROR, 400) - DTO field validation failed
     * - AuthException (NO_VALID_FIELDS, 400) - No updatable fields provided (service-level check)
     * - AuthException (VALIDATION_ERROR, 400) - Service-level business validation failed
     * - AuthException (USER_NOT_FOUND, 404) - User not found
     * - Exception caught by GlobalExceptionHandler for unexpected errors
     */
    @PatchMapping
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new com.snapitt.backend_service.modules.auth.common.exception.AuthException(
                    "Authentication required",
                    "UNAUTHORIZED",
                    org.springframework.http.HttpStatus.UNAUTHORIZED
            );
        }

        String userId = ((UserPrincipal) auth.getPrincipal()).getUser().getId();
        ProfileResponse updated = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(updated);
    }
}
