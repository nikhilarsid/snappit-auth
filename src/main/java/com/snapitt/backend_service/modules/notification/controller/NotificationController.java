package com.snapitt.backend_service.modules.notification.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.notification.dto.response.NotificationDto;
import com.snapitt.backend_service.modules.notification.dto.response.PaginatedNotificationsResponse;
import com.snapitt.backend_service.modules.notification.service.NotificationService;
import com.snapitt.backend_service.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthException("Authentication required", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDto> getNotification(
            @PathVariable String notificationId) {
        log.info("GET /api/v1/notifications/{}", notificationId);
        String userId = getCurrentUserId();

        NotificationDto notification = notificationService.getNotificationById(notificationId, userId);
        return ResponseEntity.ok(notification);
    }

    @GetMapping
    public ResponseEntity<PaginatedNotificationsResponse> getNotifications(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            int limit,
            @RequestParam(required = false) String cursor) {
        log.info("GET /api/v1/notifications - limit: {}, cursor: {}", limit, cursor);
        String userId = getCurrentUserId();

        PaginatedNotificationsResponse response = notificationService.getNotifications(userId, limit, cursor);
        return ResponseEntity.ok(response);
    }
}
