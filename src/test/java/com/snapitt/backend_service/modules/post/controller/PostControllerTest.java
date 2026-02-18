package com.snapitt.backend_service.modules.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.post.dto.request.CreatePostRequest;
import com.snapitt.backend_service.modules.post.dto.response.PaginatedPostsResponse;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
import com.snapitt.backend_service.modules.post.service.PostService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostController Unit Tests")
class PostControllerTest {

    @Mock private PostService postService;

    @InjectMocks
    private PostController postController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(postController)
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
    @DisplayName("GET /api/v1/posts/{postId}")
    class GetPostTests {

        @Test
        @DisplayName("should return 200 with post")
        void getPost_success() throws Exception {
            PostResponse response = PostResponse.builder()
                    .id("post-1").authorUsername("author").mediaUrl("http://img.url").build();

            when(postService.getPost("post-1", "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/posts/post-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("post-1"))
                    .andExpect(jsonPath("$.authorUsername").value("author"));
        }

        @Test
        @DisplayName("should return 404 when post not found")
        void getPost_notFound() throws Exception {
            when(postService.getPost("nonexistent", "user-1"))
                    .thenThrow(new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            mockMvc.perform(get("/api/v1/posts/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("POST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/posts")
    class CreatePostTests {

        @Test
        @DisplayName("should return 201 with created post")
        void createPost_success() throws Exception {
            PostResponse response = PostResponse.builder()
                    .id("post-new").authorUsername("testuser").mediaUrl("http://img.url")
                    .caption("My caption").build();

            when(postService.createPost(eq("user-1"), any(CreatePostRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreatePostRequest("http://img.url", "My caption"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("post-new"));
        }

        @Test
        @DisplayName("should return 400 for validation error")
        void createPost_validationError() throws Exception {
            mockMvc.perform(post("/api/v1/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreatePostRequest("", "caption"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/posts/{postId}")
    class DeletePostTests {

        @Test
        @DisplayName("should return 200 with action response")
        void deletePost_success() throws Exception {
            doNothing().when(postService).deletePost("post-1", "user-1");

            mockMvc.perform(delete("/api/v1/posts/post-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("POST_DELETED"));
        }

        @Test
        @DisplayName("should return 403 when not author")
        void deletePost_forbidden() throws Exception {
            doThrow(new AuthException("Forbidden", "FORBIDDEN", HttpStatus.FORBIDDEN))
                    .when(postService).deletePost("post-1", "user-1");

            mockMvc.perform(delete("/api/v1/posts/post-1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/posts/{postId}/like")
    class LikePostTests {

        @Test
        @DisplayName("should return 200 for successful like")
        void likePost_success() throws Exception {
            doNothing().when(postService).likePost("post-1", "user-1");

            mockMvc.perform(post("/api/v1/posts/post-1/like"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("POST_LIKED"));
        }

        @Test
        @DisplayName("should return 409 when already liked")
        void likePost_alreadyLiked() throws Exception {
            doThrow(new AuthException("Already liked", "ALREADY_LIKED", HttpStatus.CONFLICT))
                    .when(postService).likePost("post-1", "user-1");

            mockMvc.perform(post("/api/v1/posts/post-1/like"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ALREADY_LIKED"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/posts/{postId}/like")
    class UnlikePostTests {

        @Test
        @DisplayName("should return 200 for successful unlike")
        void unlikePost_success() throws Exception {
            doNothing().when(postService).unlikePost("post-1", "user-1");

            mockMvc.perform(delete("/api/v1/posts/post-1/like"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("POST_UNLIKED"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/me")
    class GetMyPostsTests {

        @Test
        @DisplayName("should return 200 with paginated posts")
        void getMyPosts_success() throws Exception {
            PaginatedPostsResponse response = PaginatedPostsResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(postService.getMyPosts("user-1", 20, null)).thenReturn(response);

            mockMvc.perform(get("/api/v1/posts/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/user/{username}")
    class GetPostsByUsernameTests {

        @Test
        @DisplayName("should return 200 with posts")
        void getPostsByUsername_success() throws Exception {
            PaginatedPostsResponse response = PaginatedPostsResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(postService.getPostsByUsername("author", 20, null, "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/posts/user/author"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
