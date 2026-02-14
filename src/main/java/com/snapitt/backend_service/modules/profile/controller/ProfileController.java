package com.snapitt.backend_service.modules.profile.controller;

import com.snapitt.backend_service.modules.profile.dto.request.UpdateProfileRequest;
import com.snapitt.backend_service.modules.profile.dto.response.ProfileResponse;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/{username}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String username) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String viewerId = null;
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            viewerId = ((UserPrincipal) auth.getPrincipal()).getUser().getId();
        }

        ProfileResponse profile = profileService.getProfile(viewerId, username);
        return ResponseEntity.ok(profile);
    }

    @PatchMapping
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            return ResponseEntity.status(401).build();
        }

        String userId = ((UserPrincipal) auth.getPrincipal()).getUser().getId();
        ProfileResponse updated = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(updated);
    }
}
