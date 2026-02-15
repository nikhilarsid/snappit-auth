package com.snapitt.backend_service.modules.story.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.story.dto.request.CreateStoryRequest;
import com.snapitt.backend_service.modules.story.dto.response.PaginatedStoriesResponse;
import com.snapitt.backend_service.modules.story.dto.response.StoryResponse;
import com.snapitt.backend_service.modules.story.dto.response.StoryActionResponse;
import com.snapitt.backend_service.modules.story.service.StoryService;
import com.snapitt.backend_service.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * StoryController - Handles story operations
 *
 * All endpoints except GET /api/v1/stories/{storyId} and GET /api/v1/stories/user/{username} require authentication.
 * Story ID validated via custom ObjectId validator.
 * Username path parameter validated via @Pattern annotation.
 */
@RestController
@Validated
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

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
     * Extract authenticated user ID from security context (optional)
     * @return User ID if authenticated, null otherwise
     */
    private String getOptionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            return null;
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    /**
     * Get story by ID
     * GET /api/v1/stories/{storyId}
     *
     * Access Control: Viewer must be the story author or an approved follower of the story author
     *
     * @param storyId MongoDB ObjectId
     * @return StoryResponse with story details
     * @throws AuthException (INVALID_STORY_ID, 400) - Invalid ObjectId format
     * @throws AuthException (STORY_NOT_FOUND, 404) - Story not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to this story
     */
    @GetMapping("/{storyId}")
    public ResponseEntity<StoryResponse> getStory(
            @PathVariable String storyId) {
        String viewerId = getOptionalUserId();
        StoryResponse story = storyService.getStory(storyId, viewerId);
        return ResponseEntity.ok(story);
    }

    /**
     * Get stories by username (paginated)
     * GET /api/v1/stories/user/{username}?limit=20&cursor=...
     *
     * Access Control: Viewer must be the story author or an approved follower of the story author
     *
     * @param username Author's username (3-30 chars, pattern: ^[a-zA-Z0-9_.-]{3,30}$)
     * @param limit Items per page (1-50, default 20)
     * @param cursor Opaque cursor for pagination
     * @return PaginatedStoriesResponse with stories and nextCursor
     * @throws AuthException (INVALID_USERNAME, 400) - Invalid username format
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to view these stories
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<PaginatedStoriesResponse> getStoriesByUsername(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String viewerId = getOptionalUserId();
        PaginatedStoriesResponse result = storyService.getStoriesByUsername(username, limit, cursor, viewerId);
        return ResponseEntity.ok(result);
    }

    /**
     * Create a new story
     * POST /api/v1/stories
     *
     * Auth: Required
     *
     * @param createRequest Story creation request (mediaUrl required, expiresAt required)
     * @return StoryResponse with created story (201 Created)
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (VALIDATION_ERROR, 400) - mediaUrl blank or expiresAt in the past
     */
    @PostMapping
    public ResponseEntity<StoryResponse> createStory(
            @Valid @RequestBody CreateStoryRequest createRequest) {
        String authorId = getCurrentUserId();
        StoryResponse created = storyService.createStory(authorId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get a story belonging to the authenticated user
     * GET /api/v1/stories/me/{storyId}
     *
     * Auth: Required
     */
    @GetMapping("/me/{storyId}")
    public ResponseEntity<StoryResponse> getMyStory(
            @PathVariable String storyId) {
        String userId = getCurrentUserId();
        StoryResponse story = storyService.getMyStory(storyId, userId);
        return ResponseEntity.ok(story);
    }

    /**
     * Get stories for the authenticated user (paginated)
     * GET /api/v1/stories/me?limit=20&cursor=...
     *
     * Auth: Required
     */
    @GetMapping("/me")
    public ResponseEntity<PaginatedStoriesResponse> getMyStories(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String userId = getCurrentUserId();
        PaginatedStoriesResponse result = storyService.getMyStories(userId, limit, cursor);
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a story
     * DELETE /api/v1/stories/{storyId}
     *
     * Auth: Required (must be story author)
     *
     * @param storyId Story ID to delete
     * @return StoryActionResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (STORY_NOT_FOUND, 404) - Story not found
     * @throws AuthException (FORBIDDEN, 403) - Not story author
     */
    @DeleteMapping("/{storyId}")
    public ResponseEntity<StoryActionResponse> deleteStory(
            @PathVariable String storyId) {
        String authorId = getCurrentUserId();
        storyService.deleteStory(storyId, authorId);
        return ResponseEntity.ok(StoryActionResponse.of("STORY_DELETED"));
    }
}
