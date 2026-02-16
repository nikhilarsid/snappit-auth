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

@RestController
@Validated
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

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

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @PathVariable String postId) {
        String viewerId = getOptionalUserId();
        PostResponse post = postService.getPost(postId, viewerId);
        return ResponseEntity.ok(post);
    }

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

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest createRequest) {
        String authorId = getCurrentUserId();
        PostResponse created = postService.createPost(authorId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me/{postId}")
    public ResponseEntity<PostResponse> getMyPost(
            @PathVariable String postId) {
        String userId = getCurrentUserId();
        PostResponse post = postService.getMyPost(postId, userId);
        return ResponseEntity.ok(post);
    }

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

    @DeleteMapping("/{postId}")
    public ResponseEntity<PostActionResponse> deletePost(
            @PathVariable String postId) {
        String authorId = getCurrentUserId();
        postService.deletePost(postId, authorId);
        return ResponseEntity.ok(PostActionResponse.of("POST_DELETED"));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<PostActionResponse> likePost(
            @PathVariable String postId) {
        String userId = getCurrentUserId();
        postService.likePost(postId, userId);
        return ResponseEntity.ok(PostActionResponse.of("POST_LIKED"));
    }

    @DeleteMapping("/{postId}/like")
    public ResponseEntity<PostActionResponse> unlikePost(
            @PathVariable String postId) {
        String userId = getCurrentUserId();
        postService.unlikePost(postId, userId);
        return ResponseEntity.ok(PostActionResponse.of("POST_UNLIKED"));
    }
}
