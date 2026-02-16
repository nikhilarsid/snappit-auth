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

@RestController
@Validated
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthException("Authentication required", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    private String getOptionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            return null;
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    @GetMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> getComment(
            @PathVariable String postId,
            @PathVariable String commentId) {
        String viewerId = getOptionalUserId();
        CommentResponse comment = commentService.getComment(commentId, viewerId);
        return ResponseEntity.ok(comment);
    }

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

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest createRequest) {
        String authorId = getCurrentUserId();
        CommentResponse created = commentService.createComment(postId, authorId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<CommentActionResponse> deleteComment(
            @PathVariable String commentId) {
        String authorId = getCurrentUserId();
        commentService.deleteComment(commentId, authorId);
        return ResponseEntity.ok(CommentActionResponse.of("COMMENT_DELETED"));
    }
}
