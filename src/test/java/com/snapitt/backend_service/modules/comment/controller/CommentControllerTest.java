package com.snapitt.backend_service.modules.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.comment.dto.request.CreateCommentRequest;
import com.snapitt.backend_service.modules.comment.dto.response.CommentResponse;
import com.snapitt.backend_service.modules.comment.dto.response.PaginatedCommentsResponse;
import com.snapitt.backend_service.modules.comment.service.CommentService;
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
@DisplayName("CommentController Unit Tests")
class CommentControllerTest {

    @Mock private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController)
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
    @DisplayName("GET /api/v1/posts/{postId}/comments/{commentId}")
    class GetCommentTests {

        @Test
        @DisplayName("should return 200 with comment")
        void getComment_success() throws Exception {
            CommentResponse response = CommentResponse.builder()
                    .id("comment-1").postId("post-1").authorUsername("author").text("Hello!").build();

            when(commentService.getComment("comment-1", "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/posts/post-1/comments/comment-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("comment-1"))
                    .andExpect(jsonPath("$.text").value("Hello!"));
        }

        @Test
        @DisplayName("should return 404 when comment not found")
        void getComment_notFound() throws Exception {
            when(commentService.getComment("nonexistent", "user-1"))
                    .thenThrow(new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

            mockMvc.perform(get("/api/v1/posts/post-1/comments/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("COMMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/{postId}/comments")
    class GetCommentsByPostTests {

        @Test
        @DisplayName("should return 200 with comments list")
        void getCommentsByPost_success() throws Exception {
            PaginatedCommentsResponse response = PaginatedCommentsResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(commentService.getCommentsByPost("post-1", 20, null, "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/posts/post-1/comments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/posts/{postId}/comments")
    class CreateCommentTests {

        @Test
        @DisplayName("should return 201 with created comment")
        void createComment_success() throws Exception {
            CommentResponse response = CommentResponse.builder()
                    .id("comment-new").postId("post-1").authorUsername("testuser").text("Nice post!").build();

            when(commentService.createComment(eq("post-1"), eq("user-1"), any(CreateCommentRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/posts/post-1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreateCommentRequest("Nice post!", null, null))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("comment-new"))
                    .andExpect(jsonPath("$.text").value("Nice post!"));
        }

        @Test
        @DisplayName("should return 400 for blank text")
        void createComment_blankText() throws Exception {
            mockMvc.perform(post("/api/v1/posts/post-1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreateCommentRequest("", null, null))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/posts/comments/{commentId}")
    class DeleteCommentTests {

        @Test
        @DisplayName("should return 200 with COMMENT_DELETED action")
        void deleteComment_success() throws Exception {
            doNothing().when(commentService).deleteComment("comment-1", "user-1");

            mockMvc.perform(delete("/api/v1/posts/comments/comment-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("COMMENT_DELETED"));
        }

        @Test
        @DisplayName("should return 403 when not authorized")
        void deleteComment_forbidden() throws Exception {
            doThrow(new AuthException("Forbidden", "FORBIDDEN", HttpStatus.FORBIDDEN))
                    .when(commentService).deleteComment("comment-1", "user-1");

            mockMvc.perform(delete("/api/v1/posts/comments/comment-1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/{postId}/comments/{commentId}/replies")
    class GetRepliesTests {

        @Test
        @DisplayName("should return 200 with replies list")
        void getReplies_success() throws Exception {
            PaginatedCommentsResponse response = PaginatedCommentsResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(commentService.getCommentsByReply("comment-1", "post-1", 20, null, "user-1"))
                    .thenReturn(response);

            mockMvc.perform(get("/api/v1/posts/post-1/comments/comment-1/replies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
