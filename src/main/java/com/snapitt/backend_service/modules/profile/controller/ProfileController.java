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

@RestController
@RequestMapping("/v1/profile")
@RequiredArgsConstructor
@Validated
public class ProfileController {

    private final ProfileService profileService;

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
