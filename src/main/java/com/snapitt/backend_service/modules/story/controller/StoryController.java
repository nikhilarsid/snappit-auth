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

@RestController
@Validated
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

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

    @GetMapping("/{storyId}")
    public ResponseEntity<StoryResponse> getStory(
            @PathVariable String storyId) {
        String viewerId = getOptionalUserId();
        StoryResponse story = storyService.getStory(storyId, viewerId);
        return ResponseEntity.ok(story);
    }

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

    @PostMapping
    public ResponseEntity<StoryResponse> createStory(
            @Valid @RequestBody CreateStoryRequest createRequest) {
        String authorId = getCurrentUserId();
        StoryResponse created = storyService.createStory(authorId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me/{storyId}")
    public ResponseEntity<StoryResponse> getMyStory(
            @PathVariable String storyId) {
        String userId = getCurrentUserId();
        StoryResponse story = storyService.getMyStory(storyId, userId);
        return ResponseEntity.ok(story);
    }

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

    @DeleteMapping("/{storyId}")
    public ResponseEntity<StoryActionResponse> deleteStory(
            @PathVariable String storyId) {
        String authorId = getCurrentUserId();
        storyService.deleteStory(storyId, authorId);
        return ResponseEntity.ok(StoryActionResponse.of("STORY_DELETED"));
    }
}
