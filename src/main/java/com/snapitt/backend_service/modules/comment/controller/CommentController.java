package com.snapitt.backend_service.modules.comment.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.comment.dto.request.CreateCommentRequest;
import com.snapitt.backend_service.modules.comment.dto.response.CommentResponse;
import com.snapitt.backend_service.modules.comment.dto.response.PaginatedCommentsResponse;
import com.snapitt.backend_service.modules.comment.dto.response.CommentActionResponse;
import com.snapitt.backend_service.modules.comment.service.CommentService;
import com.snapitt.backend_service.security.UserPrincipal;
import jakarta.validation.Valid;
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
 * CommentController - Handles comment operations
 *
 * GET endpoints require optional authentication (access control enforced)
 * POST/DELETE endpoints require authentication
 */
@RestController
@Validated
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

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
     * Get comment by ID
     * GET /api/v1/posts/{postId}/comments/{commentId}
     *
     * Access Control: User must have access to the post
     *
     * @param postId Post ID
     * @param commentId Comment ID
     * @return CommentResponse with comment details
     * @throws AuthException (COMMENT_NOT_FOUND, 404) - Comment not found
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to the post
     */
    @GetMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> getComment(
            @PathVariable String postId,
            @PathVariable String commentId) {
        String viewerId = getOptionalUserId();
        CommentResponse comment = commentService.getComment(commentId, viewerId);
        return ResponseEntity.ok(comment);
    }

    /**
     * Get paginated comments for a post
     * GET /api/v1/posts/{postId}/comments?limit=20&cursor=...
     *
     * Access Control: User must have access to the post
     *
     * @param postId Post ID
     * @param limit Items per page (1-50, default 20)
     * @param cursor Opaque cursor for pagination
     * @return PaginatedCommentsResponse with comments and nextCursor
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to the post
     */
    @GetMapping("/{postId}/comments")
    public ResponseEntity<PaginatedCommentsResponse> getCommentsByPost(
            @PathVariable String postId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String viewerId = getOptionalUserId();
        PaginatedCommentsResponse result = commentService.getCommentsByPost(postId, limit, cursor, viewerId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get paginated replies to a comment
     * GET /api/v1/posts/{postId}/comments/{commentId}/replies?limit=20&cursor=...
     *
     * Access Control: User must have access to the post
     *
     * @param postId Post ID
     * @param commentId Parent comment ID
     * @param limit Items per page (1-50, default 20)
     * @param cursor Opaque cursor for pagination
     * @return PaginatedCommentsResponse with replies and nextCursor
     * @throws AuthException (COMMENT_NOT_FOUND, 404) - Parent comment not found
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to the post
     */
    @GetMapping("/{postId}/comments/{commentId}/replies")
    public ResponseEntity<PaginatedCommentsResponse> getCommentReplies(
            @PathVariable String postId,
            @PathVariable String commentId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        String viewerId = getOptionalUserId();
        PaginatedCommentsResponse result = commentService.getCommentsByReply(commentId, postId, limit, cursor, viewerId);
        return ResponseEntity.ok(result);
    }

    /**
     * Create a comment or reply
     * POST /api/v1/posts/{postId}/comments
     *
     * Auth: Required
     *
     * @param postId Post ID to comment on
     * @param createRequest Comment creation request
     * @return CommentResponse with created comment (201 Created)
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (COMMENT_NOT_FOUND, 404) - Parent comment not found (if replying)
     * @throws AuthException (FORBIDDEN, 403) - Not authorized to comment on this post
     * @throws AuthException (VALIDATION_ERROR, 400) - Comment text blank or invalid
     */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest createRequest) {
        String authorId = getCurrentUserId();
        CommentResponse created = commentService.createComment(postId, authorId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Delete a comment
     * DELETE /api/v1/comments/{commentId}
     *
     * Auth: Required (must be comment author or post author)
     *
     * @param commentId Comment ID to delete
     * @return CommentActionResponse with success message
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (COMMENT_NOT_FOUND, 404) - Comment not found or already deleted
     * @throws AuthException (FORBIDDEN, 403) - Not authorized to delete this comment
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<CommentActionResponse> deleteComment(
            @PathVariable String commentId) {
        String authorId = getCurrentUserId();
        commentService.deleteComment(commentId, authorId);
        return ResponseEntity.ok(CommentActionResponse.of("COMMENT_DELETED"));
    }
}
