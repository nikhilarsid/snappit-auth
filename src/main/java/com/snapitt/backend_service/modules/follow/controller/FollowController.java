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

/**
 * FollowController - Handles follow operations
 *
 * All endpoints require authentication.
 * Username path parameter validated via @Pattern annotation.
 * Pagination query parameters validated via @Min/@Max annotations.
 */
@RestController
@Validated
@RequestMapping("/api/v1/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /**
     * Extract authenticated user ID from security context
     * @throws AuthException (UNAUTHORIZED, 401) if user not authenticated
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthException("Authentication required", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    /**
     * Create follow request to target user
     * POST /api/v1/follow/{username}
     *
     * @param username Target user's username (3-30 chars, alphanumeric + _.- )
     * @return FollowResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (USER_NOT_FOUND, 404) - Target user not found
     * @throws AuthException (CANNOT_FOLLOW_SELF, 400) - Cannot follow self
     * @throws AuthException (ALREADY_FOLLOWING, 409) - Already following
     * @throws AuthException (REQUEST_ALREADY_SENT, 409) - Request already exists
     */
    @PostMapping("/{username}")
    public ResponseEntity<FollowResponse> createFollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String followerId = getCurrentUserId();
        followService.createFollowRequest(followerId, username);
        return ResponseEntity.ok(FollowResponse.of("FOLLOW_REQUEST_SENT"));
    }

    /**
     * Approve follow request from user
     * POST /api/v1/follow/{username}/approve
     *
     * @param username Username of requester who wants to follow the authenticated user
     * @return FollowResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (USER_NOT_FOUND, 404) - Requester not found
     * @throws AuthException (NO_PENDING_REQUEST, 404) - No pending request found
     */
    @PostMapping("/{username}/approve")
    public ResponseEntity<FollowResponse> approveFollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String currentUserId = getCurrentUserId();
        followService.approveFollow(currentUserId, username);
        return ResponseEntity.ok(FollowResponse.of("FOLLOW_APPROVED"));
    }

    /**
     * Reject follow request from user
     * POST /api/v1/follow/{username}/reject
     *
     * @param username Username of requester
     * @return FollowResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (USER_NOT_FOUND, 404) - Requester not found
     * @throws AuthException (NO_PENDING_REQUEST, 404) - No pending request found
     */
    @PostMapping("/{username}/reject")
    public ResponseEntity<FollowResponse> rejectFollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String currentUserId = getCurrentUserId();
        followService.rejectFollow(currentUserId, username);
        return ResponseEntity.ok(FollowResponse.of("FOLLOW_REJECTED"));
    }

    /**
     * Unfollow a user
     * DELETE /api/v1/follow/{username}
     *
     * @param username Username to unfollow
     * @return FollowResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (CANNOT_UNFOLLOW_SELF, 400) - Cannot unfollow self
     * @throws AuthException (NOT_FOLLOWING, 404) - Not currently following
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<FollowResponse> unfollow(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username) {
        String followerId = getCurrentUserId();
        followService.unfollow(followerId, username);
        return ResponseEntity.ok(FollowResponse.of("UNFOLLOWED"));
    }

    /**
     * Get followers of a user (paginated)
     * GET /api/v1/follow/{username}/followers?limit=20&cursor=...
     *
     * @param username Target user's username
     * @param limit Items per page (1-50, default 20)
     * @param cursor Opaque continuation cursor for next page
     * @return PaginatedFollowersResponse with follower list and nextCursor
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     */
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

    /**
     * Get followers for the authenticated user (paginated)
     * GET /api/v1/follow/my/followers?limit=20&cursor=...
     */
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

    /**
     * Get following list of a user (paginated)
     * GET /api/v1/follow/{username}/following?limit=20&cursor=...
     *
     * @param username Target user's username
     * @param limit Items per page (1-50, default 20)
     * @param cursor Opaque continuation cursor for next page
     * @return PaginatedFollowingResponse with following list and nextCursor
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     */
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
        PaginatedFollowingResponse result = followService.getFollowing(username, limit, cursor);
        return ResponseEntity.ok(result);
    }

    /**
     * Get following list for the authenticated user (paginated)
     * GET /api/v1/follow/my/following?limit=20&cursor=...
     */
    @GetMapping("/my/following")
    public ResponseEntity<PaginatedFollowingResponse> getMyFollowing(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String currentUserId = getCurrentUserId();
        PaginatedFollowingResponse result = followService.getFollowingByUserId(currentUserId, limit, cursor);
        return ResponseEntity.ok(result);
    }
}
