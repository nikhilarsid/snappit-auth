package com.snapitt.backend_service.modules.post.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.post.dto.request.CreatePostRequest;
import com.snapitt.backend_service.modules.post.dto.response.PaginatedPostsResponse;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
import com.snapitt.backend_service.modules.post.dto.response.PostActionResponse;
import com.snapitt.backend_service.modules.post.service.PostService;
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
 * PostController - Handles post operations
 *
 * All endpoints except GET /api/v1/posts/{postId} and GET /api/v1/posts/user/{username} require authentication.
 * Post ID validated via custom ObjectId validator.
 * Username path parameter validated via @Pattern annotation.
 */
@RestController
@Validated
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

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
     * Get post by ID
     * GET /api/v1/posts/{postId}
     *
     * Access Control: Viewer must be the post author or an approved follower of the post author
     *
     * @param postId MongoDB ObjectId
     * @return PostResponse with post details
     * @throws AuthException (INVALID_POST_ID, 400) - Invalid ObjectId format
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to this post
     */
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @PathVariable String postId) {
        String viewerId = getOptionalUserId();
        PostResponse post = postService.getPost(postId, viewerId);
        return ResponseEntity.ok(post);
    }

    /**
     * Get posts by username (paginated)
     * GET /api/v1/posts/user/{username}?limit=20&cursor=...
     *
     * Access Control: Viewer must be the post author or an approved follower of the post author
     *
     * @param username Author's username (3-30 chars, pattern: ^[a-zA-Z0-9_.-]{3,30}$)
     * @param limit Items per page (1-50, default 20)
     * @param cursor Opaque cursor for pagination
     * @return PaginatedPostsResponse with posts and nextCursor
     * @throws AuthException (INVALID_USERNAME, 400) - Invalid username format
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to view these posts
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<PaginatedPostsResponse> getPostsByUsername(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,30}$", message = "Username format is invalid")
            String username,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String viewerId = getOptionalUserId();
        PaginatedPostsResponse result = postService.getPostsByUsername(username, limit, cursor, viewerId);
        return ResponseEntity.ok(result);
    }

    /**
     * Create a new post
     * POST /api/v1/posts
     *
     * Auth: Required
     *
     * @param createRequest Post creation request (mediaUrl required, caption optional)
     * @return PostResponse with created post (201 Created)
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (VALIDATION_ERROR, 400) - mediaUrl blank or caption too long
     */
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest createRequest) {
        String authorId = getCurrentUserId();
        PostResponse created = postService.createPost(authorId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get a post belonging to the authenticated user
     * GET /api/v1/posts/me/{postId}
     *
     * Auth: Required
     */
    @GetMapping("/me/{postId}")
    public ResponseEntity<PostResponse> getMyPost(
            @PathVariable String postId) {
        String userId = getCurrentUserId();
        PostResponse post = postService.getMyPost(postId, userId);
        return ResponseEntity.ok(post);
    }

    /**
     * Get posts for the authenticated user (paginated)
     * GET /api/v1/posts/me?limit=20&cursor=...
     *
     * Auth: Required
     */
    @GetMapping("/me")
    public ResponseEntity<PaginatedPostsResponse> getMyPosts(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String userId = getCurrentUserId();
        PaginatedPostsResponse result = postService.getMyPosts(userId, limit, cursor);
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a post
     * DELETE /api/v1/posts/{postId}
     *
     * Auth: Required (must be post author)
     *
     * @param postId Post ID to delete
     * @return PostActionResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Not post author
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<PostActionResponse> deletePost(
            @PathVariable String postId) {
        String authorId = getCurrentUserId();
        postService.deletePost(postId, authorId);
        return ResponseEntity.ok(PostActionResponse.of("POST_DELETED"));
    }

    /**
     * Like a post
     * POST /api/v1/posts/{postId}/like
     *
     * Auth: Required
     *
     * @param postId Post ID to like
     * @return PostActionResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (ALREADY_LIKED, 409) - Already liked this post
     */
    @PostMapping("/{postId}/like")
    public ResponseEntity<PostActionResponse> likePost(
            @PathVariable String postId) {
        String userId = getCurrentUserId();
        postService.likePost(postId, userId);
        return ResponseEntity.ok(PostActionResponse.of("POST_LIKED"));
    }

    /**
     * Unlike a post
     * DELETE /api/v1/posts/{postId}/like
     *
     * Auth: Required
     *
     * @param postId Post ID to unlike
     * @return PostActionResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (NOT_LIKED, 409) - Post not currently liked by user
     */
    @DeleteMapping("/{postId}/like")
    public ResponseEntity<PostActionResponse> unlikePost(
            @PathVariable String postId) {
        String userId = getCurrentUserId();
        postService.unlikePost(postId, userId);
        return ResponseEntity.ok(PostActionResponse.of("POST_UNLIKED"));
    }
}

