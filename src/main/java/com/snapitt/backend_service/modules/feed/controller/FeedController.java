package com.snapitt.backend_service.modules.feed.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedPostsResponse;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedStoriesResponse;
import com.snapitt.backend_service.modules.feed.service.FeedService;
import com.snapitt.backend_service.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * FeedController - Handles feed operations (stories and posts)
 *
 * All endpoints require JWT authentication.
 * Authorization: Only authenticated users can access their own feed.
 * Parameter Validation: limit (1-50), cursor (optional, opaque string).
 *
 * Endpoints:
 * - GET /api/v1/feed/posts              - Get user's post feed (cursor paginated)
 * - GET /api/v1/feed/stories            - Get user's story feed (cursor paginated)
 * - PATCH /api/v1/feed/see/post/{id}    - Mark a post as seen
 * - PATCH /api/v1/feed/see/story/{id}   - Mark a story as seen
 */
@RestController
@Validated
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

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
     * Get paginated post feed for authenticated user
     *
     * Features:
     * - Cursor-based pagination
     * - Ordered by: unseen first, then fresh (newest first)
     * - Only shows posts from approved followers
     *
     * Query Parameters:
     * - limit: Items per page (1-50, default 20)
     * - cursor: Pagination cursor (null for first page)
     *
     * Response:
     * - 200 OK: PaginatedFeedPostsResponse with posts and nextCursor
     * - 400 BAD_REQUEST: Invalid limit parameter
     * - 401 UNAUTHORIZED: Not authenticated
     * - 500 INTERNAL_SERVER_ERROR: Unexpected errors
     *
     * @param limit Items per page (1-50)
     * @param cursor Pagination cursor from previous response (optional)
     * @return ResponseEntity with PaginatedFeedPostsResponse
     */
    @GetMapping("/posts")
    public ResponseEntity<PaginatedFeedPostsResponse> getMyFeedPosts(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) String cursor
    ) {
        String userId = getCurrentUserId();
        PaginatedFeedPostsResponse response = feedService.getMyFeedPosts(userId, limit, cursor);
        return ResponseEntity.ok(response);
    }

    /**
     * Get paginated story feed for authenticated user
     *
     * Features:
     * - Cursor-based pagination
     * - One entry per story creator
     * - Ordered by: unseen first, then latest (newest stories first)
     * - Only shows stories from approved followers
     *
     * Query Parameters:
     * - limit: Items per page (1-50, default 20)
     * - cursor: Pagination cursor (null for first page)
     *
     * Response:
     * - 200 OK: PaginatedFeedStoriesResponse with stories and nextCursor
     * - 400 BAD_REQUEST: Invalid limit parameter
     * - 401 UNAUTHORIZED: Not authenticated
     * - 500 INTERNAL_SERVER_ERROR: Unexpected errors
     *
     * @param limit Items per page (1-50)
     * @param cursor Pagination cursor from previous response (optional)
     * @return ResponseEntity with PaginatedFeedStoriesResponse
     */
    @GetMapping("/stories")
    public ResponseEntity<PaginatedFeedStoriesResponse> getMyFeedStories(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) String cursor
    ) {
        String userId = getCurrentUserId();
        PaginatedFeedStoriesResponse response = feedService.getMyFeedStories(userId, limit, cursor);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark a post as seen in user's feed
     *
     * Features:
     * - Updates post_feed record to seen = true
     * - Allows maintaining freshness ordering on subsequent feed requests
     * - Only post owner can mark their own feed posts
     *
     * Path Parameters:
     * - postFeedId: Post feed record ID (post_feed._id)
     *
     * Response:
     * - 200 OK: Post marked as read
     * - 401 UNAUTHORIZED: Not authenticated
     * - 403 FORBIDDEN: Feed record doesn't belong to user
     * - 404 NOT_FOUND: Feed record not found
     * - 500 INTERNAL_SERVER_ERROR: Unexpected errors
     *
     * @param postFeedId Post feed record ID from feed response
     * @return ResponseEntity with 200 OK
     */
    @PatchMapping("/see/post/{postFeedId}")
    public ResponseEntity<Void> markPostAsSeen(
            @PathVariable String postFeedId
    ) {
        String userId = getCurrentUserId();
        feedService.markPostAsRead(postFeedId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Mark a story as seen in user's feed
     *
     * Features:
     * - Updates story_feed record to seen = true
     * - Allows maintaining freshness ordering on subsequent feed requests
     * - Only story viewer can mark their own feed stories
     *
     * Path Parameters:
     * - storyFeedId: Story feed record ID (story_feed._id)
     *
     * Response:
     * - 200 OK: Story marked as read
     * - 401 UNAUTHORIZED: Not authenticated
     * - 403 FORBIDDEN: Feed record doesn't belong to user
     * - 404 NOT_FOUND: Feed record not found
     * - 500 INTERNAL_SERVER_ERROR: Unexpected errors
     *
     * @param storyFeedId Story feed record ID from feed response
     * @return ResponseEntity with 200 OK
     */
    @PatchMapping("/see/story/{storyFeedId}")
    public ResponseEntity<Void> markStoryAsSeen(
            @PathVariable String storyFeedId
    ) {
        String userId = getCurrentUserId();
        feedService.markStoryAsRead(storyFeedId, userId);
        return ResponseEntity.ok().build();
    }
}
