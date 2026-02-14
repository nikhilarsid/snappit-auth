package com.social.profile.controller;

import com.social.profile.dto.ProfileResponse;
import com.social.profile.dto.UpdateProfileRequest;
import com.social.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    //  NEW: Search Endpoint
    // Usage: GET /api/v1/profile/search?query=vikrant
    @GetMapping("/search")
    public ResponseEntity<List<ProfileResponse>> searchProfiles(@RequestParam String query) {
        return ResponseEntity.ok(profileService.searchProfiles(query));
    }

    //  GET /api/v1/profile/:username
    @GetMapping("/{username}")
    public ResponseEntity<ProfileResponse> getProfile(
            @PathVariable String username,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId) {

        // viewerId comes from API Gateway/Auth Middleware
        return ResponseEntity.ok(profileService.getProfile(username, viewerId));
    }

    //  PATCH /api/v1/profile
    @PatchMapping
    public ResponseEntity<ProfileResponse> updateProfile(
            @RequestHeader(value = "X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }
}