package com.snapitt.backend_service.modules.follow.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.follow.dto.request.PaginationQueryRequest;
import com.snapitt.backend_service.modules.follow.dto.response.FollowResponse;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowersResponse;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowingResponse;
import com.snapitt.backend_service.modules.follow.service.FollowService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import com.snapitt.backend_service.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthException("Authentication required", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    @PostMapping("/{username}")
    public ResponseEntity<FollowResponse> createFollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String followerId = getCurrentUserId();
        followService.createFollowRequest(followerId, username);
        return ResponseEntity.ok(FollowResponse.of("FOLLOW_REQUEST_SENT"));
    }

    @PostMapping("/{username}/approve")
    public ResponseEntity<FollowResponse> approveFollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String currentUserId = getCurrentUserId();
        followService.approveFollow(currentUserId, username);
        return ResponseEntity.ok(FollowResponse.of("FOLLOW_APPROVED"));
    }

    @PostMapping("/{username}/reject")
    public ResponseEntity<FollowResponse> rejectFollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String currentUserId = getCurrentUserId();
        followService.rejectFollow(currentUserId, username);
        return ResponseEntity.ok(FollowResponse.of("FOLLOW_REJECTED"));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<FollowResponse> unfollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String followerId = getCurrentUserId();
        String result = followService.unfollow(followerId, username);
        return ResponseEntity.ok(FollowResponse.of(result));
    }

    @GetMapping("/{username}/followers")
    public ResponseEntity<PaginatedFollowersResponse> getFollowers(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        PaginatedFollowersResponse result = followService.getFollowers(username, limit, cursor, currentUserId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my/followers")
    public ResponseEntity<PaginatedFollowersResponse> getMyFollowers(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        PaginatedFollowersResponse result = followService.getFollowersByUserId(currentUserId, limit, cursor, currentUserId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{username}/following")
    public ResponseEntity<PaginatedFollowingResponse> getFollowing(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        PaginatedFollowingResponse result = followService.getFollowing(username, limit, cursor, currentUserId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my/pending")
    public ResponseEntity<PaginatedFollowersResponse> getMyPendingRequests(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        PaginatedFollowersResponse result = followService.getPendingFollowRequests(currentUserId, limit, cursor);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my/following")
    public ResponseEntity<PaginatedFollowingResponse> getMyFollowing(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        PaginatedFollowingResponse result = followService.getFollowingByUserId(currentUserId, limit, cursor, currentUserId);
        return ResponseEntity.ok(result);
    }
}
