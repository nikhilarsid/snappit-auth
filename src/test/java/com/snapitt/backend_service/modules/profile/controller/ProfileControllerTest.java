package com.snapitt.backend_service.modules.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.profile.dto.request.UpdateProfileRequest;
import com.snapitt.backend_service.modules.profile.dto.response.ProfileResponse;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController Unit Tests")
class ProfileControllerTest {

    @Mock private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileController)
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
    @DisplayName("GET /v1/profile/{username}")
    class GetProfileTests {

        @Test
        @DisplayName("should return 200 with profile")
        void getProfile_success() throws Exception {
            ProfileResponse response = new ProfileResponse(
                    "author", "http://avatar.url", "Bio text", 10L, 5L, 3L, false, false, Instant.now());

            when(profileService.getProfile("user-1", "author")).thenReturn(response);

            mockMvc.perform(get("/v1/profile/author"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("author"))
                    .andExpect(jsonPath("$.followersCount").value(10));
        }

        @Test
        @DisplayName("should return 404 for nonexistent user")
        void getProfile_notFound() throws Exception {
            when(profileService.getProfile("user-1", "nobody"))
                    .thenThrow(new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

            mockMvc.perform(get("/v1/profile/nobody"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /v1/profile/my")
    class GetMyProfileTests {

        @Test
        @DisplayName("should return 200 with own profile")
        void getMyProfile_success() throws Exception {
            ProfileResponse response = new ProfileResponse(
                    "testuser", "http://avatar.url", "Bio", 10L, 5L, 3L, false, false, Instant.now());

            when(profileService.getMyProfile("user-1")).thenReturn(response);

            mockMvc.perform(get("/v1/profile/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("testuser"));
        }
    }

    @Nested
    @DisplayName("PATCH /v1/profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("should return 200 with updated profile")
        void updateProfile_success() throws Exception {
            ProfileResponse response = new ProfileResponse(
                    "testuser", "http://new-avatar.url", "New bio", 10L, 5L, 3L, false, false, Instant.now());

            when(profileService.updateProfile(eq("user-1"), any(UpdateProfileRequest.class))).thenReturn(response);

            UpdateProfileRequest updateReq = new UpdateProfileRequest();
            updateReq.setName("New Name");
            updateReq.setBio("New bio");
            updateReq.setAvatarUrl("http://new-avatar.url");

            mockMvc.perform(patch("/v1/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bio").value("New bio"));
        }

        @Test
        @DisplayName("should return 400 for no valid fields")
        void updateProfile_noValidFields() throws Exception {
            when(profileService.updateProfile(eq("user-1"), any(UpdateProfileRequest.class)))
                    .thenThrow(new AuthException("No updatable fields provided", "NO_VALID_FIELDS", HttpStatus.BAD_REQUEST));

            mockMvc.perform(patch("/v1/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
