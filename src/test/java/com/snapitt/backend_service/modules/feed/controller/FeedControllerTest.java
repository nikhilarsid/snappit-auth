package com.snapitt.backend_service.modules.feed.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedPostsResponse;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedStoriesResponse;
import com.snapitt.backend_service.modules.feed.service.FeedService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedController Unit Tests")
class FeedControllerTest {

    @Mock private FeedService feedService;

    @InjectMocks
    private FeedController feedController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(feedController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserEntity user = UserEntity.builder().id("user-1").username("testuser").build();
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /api/v1/feed/posts")
    class GetFeedPostsTests {

        @Test
        @DisplayName("should return 200 with feed posts")
        void getFeedPosts_success() throws Exception {
            PaginatedFeedPostsResponse response = PaginatedFeedPostsResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(feedService.getMyFeedPosts("user-1", 20, null)).thenReturn(response);

            mockMvc.perform(get("/api/v1/feed/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/feed/stories")
    class GetFeedStoriesTests {

        @Test
        @DisplayName("should return 200 with feed stories")
        void getFeedStories_success() throws Exception {
            PaginatedFeedStoriesResponse response = PaginatedFeedStoriesResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(feedService.getMyFeedStories("user-1", 20, null)).thenReturn(response);

            mockMvc.perform(get("/api/v1/feed/stories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/feed/see/post/{postFeedId}")
    class MarkPostAsSeenTests {

        @Test
        @DisplayName("should return 200 when post marked as seen")
        void markPostAsSeen_success() throws Exception {
            doNothing().when(feedService).markPostAsRead("feed-1", "user-1");

            mockMvc.perform(patch("/api/v1/feed/see/post/feed-1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 when feed record not found")
        void markPostAsSeen_notFound() throws Exception {
            doThrow(new AuthException("Feed record not found", "POST_FEED_NOT_FOUND", HttpStatus.NOT_FOUND))
                    .when(feedService).markPostAsRead("nonexistent", "user-1");

            mockMvc.perform(patch("/api/v1/feed/see/post/nonexistent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/feed/see/story/{storyFeedId}")
    class MarkStoryAsSeenTests {

        @Test
        @DisplayName("should return 200 when story marked as seen")
        void markStoryAsSeen_success() throws Exception {
            doNothing().when(feedService).markStoryAsRead("sfeed-1", "user-1");

            mockMvc.perform(patch("/api/v1/feed/see/story/sfeed-1"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/feed/see/story/by-creator/{creatorUsername}")
    class MarkStorySeenByCreatorTests {

        @Test
        @DisplayName("should return 200 when story marked by creator")
        void markStorySeenByCreator_success() throws Exception {
            doNothing().when(feedService).markStoryAsReadByCreator("user-1", "creator");

            mockMvc.perform(patch("/api/v1/feed/see/story/by-creator/creator"))
                    .andExpect(status().isOk());
        }
    }
}
