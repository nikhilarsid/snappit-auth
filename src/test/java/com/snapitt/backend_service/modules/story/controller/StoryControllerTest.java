package com.snapitt.backend_service.modules.story.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.story.dto.request.CreateStoryRequest;
import com.snapitt.backend_service.modules.story.dto.response.PaginatedStoriesResponse;
import com.snapitt.backend_service.modules.story.dto.response.StoryResponse;
import com.snapitt.backend_service.modules.story.service.StoryService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoryController Unit Tests")
class StoryControllerTest {

    @Mock private StoryService storyService;

    @InjectMocks
    private StoryController storyController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storyController)
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
    @DisplayName("GET /api/v1/stories/{storyId}")
    class GetStoryTests {

        @Test
        @DisplayName("should return 200 with story")
        void getStory_success() throws Exception {
            StoryResponse response = StoryResponse.builder()
                    .id("story-1").authorUsername("author").mediaUrl("http://story.url").build();

            when(storyService.getStory("story-1", "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/stories/story-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("story-1"));
        }

        @Test
        @DisplayName("should return 404 when story not found")
        void getStory_notFound() throws Exception {
            when(storyService.getStory("nonexistent", "user-1"))
                    .thenThrow(new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND));

            mockMvc.perform(get("/api/v1/stories/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("STORY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/stories")
    class CreateStoryTests {

        @Test
        @DisplayName("should return 201 with created story")
        void createStory_success() throws Exception {
            StoryResponse response = StoryResponse.builder()
                    .id("story-new").authorUsername("testuser").mediaUrl("http://story.url").build();

            when(storyService.createStory(eq("user-1"), any(CreateStoryRequest.class))).thenReturn(response);

            String json = objectMapper.writeValueAsString(
                    new CreateStoryRequest("http://story.url", Instant.now().plus(24, ChronoUnit.HOURS)));

            mockMvc.perform(post("/api/v1/stories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("story-new"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/stories/{storyId}")
    class DeleteStoryTests {

        @Test
        @DisplayName("should return 200 with STORY_DELETED")
        void deleteStory_success() throws Exception {
            doNothing().when(storyService).deleteStory("story-1", "user-1");

            mockMvc.perform(delete("/api/v1/stories/story-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("STORY_DELETED"));
        }

        @Test
        @DisplayName("should return 403 when not author")
        void deleteStory_forbidden() throws Exception {
            doThrow(new AuthException("Forbidden", "FORBIDDEN", HttpStatus.FORBIDDEN))
                    .when(storyService).deleteStory("story-1", "user-1");

            mockMvc.perform(delete("/api/v1/stories/story-1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/stories/me")
    class GetMyStoriesTests {

        @Test
        @DisplayName("should return 200 with paginated stories")
        void getMyStories_success() throws Exception {
            PaginatedStoriesResponse response = PaginatedStoriesResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(storyService.getMyStories("user-1", 20, null)).thenReturn(response);

            mockMvc.perform(get("/api/v1/stories/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/stories/user/{username}")
    class GetStoriesByUsernameTests {

        @Test
        @DisplayName("should return 200 with stories")
        void getStoriesByUsername_success() throws Exception {
            PaginatedStoriesResponse response = PaginatedStoriesResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(storyService.getStoriesByUsername("author", 20, null, "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/stories/user/author"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
