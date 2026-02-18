package com.snapitt.backend_service.modules.follow.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowersResponse;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowingResponse;
import com.snapitt.backend_service.modules.follow.service.FollowService;
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
@DisplayName("FollowController Unit Tests")
class FollowControllerTest {

    @Mock private FollowService followService;

    @InjectMocks
    private FollowController followController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(followController)
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
    @DisplayName("POST /api/v1/follow/{username}")
    class CreateFollowTests {

        @Test
        @DisplayName("should return 200 with FOLLOW_REQUEST_SENT")
        void createFollow_success() throws Exception {
            doNothing().when(followService).createFollowRequest("user-1", "target");

            mockMvc.perform(post("/api/v1/follow/target"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("FOLLOW_REQUEST_SENT"));
        }

        @Test
        @DisplayName("should return 409 when already following")
        void createFollow_alreadyFollowing() throws Exception {
            doThrow(new AuthException("Already following", "ALREADY_FOLLOWING", HttpStatus.CONFLICT))
                    .when(followService).createFollowRequest("user-1", "target");

            mockMvc.perform(post("/api/v1/follow/target"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ALREADY_FOLLOWING"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/follow/{username}/approve")
    class ApproveFollowTests {

        @Test
        @DisplayName("should return 200 with FOLLOW_APPROVED")
        void approveFollow_success() throws Exception {
            doNothing().when(followService).approveFollow("user-1", "follower");

            mockMvc.perform(post("/api/v1/follow/follower/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("FOLLOW_APPROVED"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/follow/{username}/reject")
    class RejectFollowTests {

        @Test
        @DisplayName("should return 200 with FOLLOW_REJECTED")
        void rejectFollow_success() throws Exception {
            doNothing().when(followService).rejectFollow("user-1", "follower");

            mockMvc.perform(post("/api/v1/follow/follower/reject"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("FOLLOW_REJECTED"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/follow/{username}")
    class UnfollowTests {

        @Test
        @DisplayName("should return 200 with UNFOLLOWED")
        void unfollow_success() throws Exception {
            when(followService.unfollow("user-1", "target")).thenReturn("UNFOLLOWED");

            mockMvc.perform(delete("/api/v1/follow/target"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("UNFOLLOWED"));
        }

        @Test
        @DisplayName("should return FOLLOW_REQUEST_WITHDRAWN for pending")
        void unfollow_withdrawn() throws Exception {
            when(followService.unfollow("user-1", "target")).thenReturn("FOLLOW_REQUEST_WITHDRAWN");

            mockMvc.perform(delete("/api/v1/follow/target"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("FOLLOW_REQUEST_WITHDRAWN"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/follow/{username}/followers")
    class GetFollowersTests {

        @Test
        @DisplayName("should return 200 with followers list")
        void getFollowers_success() throws Exception {
            PaginatedFollowersResponse response = PaginatedFollowersResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(followService.getFollowers("target", 20, null, "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/follow/target/followers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/follow/{username}/following")
    class GetFollowingTests {

        @Test
        @DisplayName("should return 200 with following list")
        void getFollowing_success() throws Exception {
            PaginatedFollowingResponse response = PaginatedFollowingResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(followService.getFollowing("target", 20, null, "user-1")).thenReturn(response);

            mockMvc.perform(get("/api/v1/follow/target/following"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/follow/my/pending")
    class GetMyPendingTests {

        @Test
        @DisplayName("should return 200 with pending requests")
        void getMyPending_success() throws Exception {
            PaginatedFollowersResponse response = PaginatedFollowersResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(followService.getPendingFollowRequests("user-1", 20, null)).thenReturn(response);

            mockMvc.perform(get("/api/v1/follow/my/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
