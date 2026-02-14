package com.snapitt.backend_service.modules.follow.controller;

import com.snapitt.backend_service.modules.follow.service.FollowService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import com.snapitt.backend_service.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) return null;
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    @PostMapping("/{username}")
    public ResponseEntity<?> createFollow(@PathVariable @NotBlank @Size(min = 1, max = 64) String username) {
        String followerId = getCurrentUserId();
        if (followerId == null) return ResponseEntity.status(401).build();

        followService.createFollowRequest(followerId, username);
        return ResponseEntity.ok(java.util.Map.of("message", "FOLLOW_REQUEST_SENT"));
    }

    @PostMapping("/{username}/approve")
    public ResponseEntity<?> approveFollow(@PathVariable @NotBlank @Size(min = 1, max = 64) String username) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return ResponseEntity.status(401).build();

        followService.approveFollow(currentUserId, username);
        return ResponseEntity.ok(java.util.Map.of("message", "FOLLOW_APPROVED"));
    }

    @PostMapping("/{username}/reject")
    public ResponseEntity<?> rejectFollow(@PathVariable @NotBlank @Size(min = 1, max = 64) String username) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return ResponseEntity.status(401).build();

        followService.rejectFollow(currentUserId, username);
        return ResponseEntity.ok(java.util.Map.of("message", "FOLLOW_REJECTED"));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<?> unfollow(@PathVariable @NotBlank @Size(min = 1, max = 64) String username) {
        String followerId = getCurrentUserId();
        if (followerId == null) return ResponseEntity.status(401).build();

        followService.unfollow(followerId, username);
        return ResponseEntity.ok(java.util.Map.of("message", "UNFOLLOWED"));
    }

    @GetMapping("/{username}/followers")
    public ResponseEntity<?> getFollowers(@PathVariable @NotBlank @Size(min = 1, max = 64) String username,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                          @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return ResponseEntity.status(401).build();

        var result = followService.getFollowers(username, limit, cursor, currentUserId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{username}/following")
    public ResponseEntity<?> getFollowing(@PathVariable @NotBlank @Size(min = 1, max = 64) String username,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                          @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return ResponseEntity.status(401).build();

        var result = followService.getFollowing(username, limit, cursor);
        return ResponseEntity.ok(result);
    }
}
