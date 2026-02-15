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

/**
 * NotificationController - Handles notification operations
 *
 * All endpoints require JWT authentication.
 * Authorization: Users can only access their own notifications.
 * Parameter Validation: limit (1-50), cursor (optional, opaque string).
 *
 * Endpoints:
 * - GET /api/v1/notifications/{notificationId}  - Get a single notification by ID
 * - GET /api/v1/notifications                   - Get user's notifications (cursor paginated)
 */
@Slf4j
@RestController
@Validated
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Extract authenticated user ID from security context
     * @throws AuthException (UNAUTHORIZED, 401) if user not authenticated
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthException("Authentication required", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        return ((UserPrincipal) auth.getPrincipal()).getUser().getId();
    }

    /**
     * Get a single notification by ID
     * GET /api/v1/notifications/{notificationId}
     *
     * @param notificationId ID of the notification to fetch
     * @return NotificationDto with actor username
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     * @throws AuthException (NOT_FOUND, 404) - Notification not found
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDto> getNotification(
            @PathVariable String notificationId) {
        log.info("GET /api/v1/notifications/{}", notificationId);
        getCurrentUserId();  // Verify authentication

        NotificationDto notification = notificationService.getNotificationById(notificationId);
        return ResponseEntity.ok(notification);
    }

    /**
     * Get paginated notifications for authenticated user
     * GET /api/v1/notifications?limit=20&cursor=...
     *
     * Features:
     * - Cursor-based pagination
     * - Ordered by: unseen first (false), then fresh (newest first)
     * - Only shows notifications for the authenticated user
     *
     * Query Parameters:
     * - limit: Items per page (1-50, default 20)
     * - cursor: Opaque pagination cursor from previous page's last item (required for page 2+)
     *
     * @param limit Items per page (1-50, default 20)
     * @param cursor Opaque continuation cursor for next page (null for first page)
     * @return PaginatedNotificationsResponse with notification list and nextCursor
     * @throws AuthException (UNAUTHORIZED, 401) - Not authenticated
     */
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
