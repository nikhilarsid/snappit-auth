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

@RestController
@Validated
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthException("Authentication required", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    @GetMapping("/posts")
    public ResponseEntity<PaginatedFeedPostsResponse> getMyFeedPosts(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) String cursor
    ) {
        String userId = getCurrentUserId();
        PaginatedFeedPostsResponse response = feedService.getMyFeedPosts(userId, limit, cursor);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stories")
    public ResponseEntity<PaginatedFeedStoriesResponse> getMyFeedStories(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) String cursor
    ) {
        String userId = getCurrentUserId();
        PaginatedFeedStoriesResponse response = feedService.getMyFeedStories(userId, limit, cursor);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/see/post/{postFeedId}")
    public ResponseEntity<Void> markPostAsSeen(
            @PathVariable String postFeedId
    ) {
        String userId = getCurrentUserId();
        feedService.markPostAsRead(postFeedId, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/see/story/{storyFeedId}")
    public ResponseEntity<Void> markStoryAsSeen(
            @PathVariable String storyFeedId
    ) {
        String userId = getCurrentUserId();
        feedService.markStoryAsRead(storyFeedId, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/see/story/by-creator/{creatorUsername}")
    public ResponseEntity<Void> markStorySeenByCreator(
            @PathVariable String creatorUsername
    ) {
        String userId = getCurrentUserId();
        feedService.markStoryAsReadByCreator(userId, creatorUsername);
        return ResponseEntity.ok().build();
    }
}
