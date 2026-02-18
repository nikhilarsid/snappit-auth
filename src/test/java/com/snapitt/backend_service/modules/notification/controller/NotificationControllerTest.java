package com.snapitt.backend_service.modules.notification.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.auth.common.exception.GlobalExceptionHandler;
import com.snapitt.backend_service.modules.notification.dto.response.NotificationDto;
import com.snapitt.backend_service.modules.notification.dto.response.PaginatedNotificationsResponse;
import com.snapitt.backend_service.modules.notification.model.NotificationType;
import com.snapitt.backend_service.modules.notification.service.NotificationService;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController Unit Tests")
class NotificationControllerTest {

    @Mock private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
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
    @DisplayName("GET /api/v1/notifications/{id}")
    class GetNotificationTests {

        @Test
        @DisplayName("should return 200 with notification")
        void getNotification_success() throws Exception {
            NotificationDto dto = NotificationDto.builder()
                    .id("notif-1").actorUsername("actor").type(NotificationType.LIKE)
                    .entityId("post-1").seen(false).createdAt(Instant.now()).build();

            when(notificationService.getNotificationById("notif-1", "user-1")).thenReturn(dto);

            mockMvc.perform(get("/api/v1/notifications/notif-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("notif-1"))
                    .andExpect(jsonPath("$.actorUsername").value("actor"));
        }

        @Test
        @DisplayName("should return 404 when notification not found")
        void getNotification_notFound() throws Exception {
            when(notificationService.getNotificationById("nonexistent", "user-1"))
                    .thenThrow(new AuthException("Notification not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

            mockMvc.perform(get("/api/v1/notifications/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/notifications")
    class GetNotificationsTests {

        @Test
        @DisplayName("should return 200 with paginated notifications")
        void getNotifications_success() throws Exception {
            PaginatedNotificationsResponse response = PaginatedNotificationsResponse.builder()
                    .data(List.of()).nextCursor(null).build();

            when(notificationService.getNotifications("user-1", 20, null)).thenReturn(response);

            mockMvc.perform(get("/api/v1/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/notifications/unseen-count")
    class GetUnseenCountTests {

        @Test
        @DisplayName("should return 200 with count")
        void getUnseenCount_success() throws Exception {
            when(notificationService.getUnseenCount("user-1")).thenReturn(5L);

            mockMvc.perform(get("/api/v1/notifications/unseen-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/notifications/mark-seen")
    class MarkAllSeenTests {

        @Test
        @DisplayName("should return 200 with status ok")
        void markAllSeen_success() throws Exception {
            doNothing().when(notificationService).markAllAsSeen("user-1");

            mockMvc.perform(post("/api/v1/notifications/mark-seen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }
}
