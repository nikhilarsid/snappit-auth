package com.snapitt.backend_service.modules.notification.dto.response;

import com.snapitt.backend_service.modules.notification.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * NotificationDto - DTO for individual notification in response
 * 
 * Contains notification details:
 * - id: Notification ID
 * - actorUsername: Username of the user who triggered the notification
 * - type: Type of notification (LIKE, COMMENT, FOLLOW)
 * - entityId: Related post/comment ID
 * - seen: Whether user has seen this notification
 * - createdAt: When the notification was created
 */
@Data
@Builder
@AllArgsConstructor
public class NotificationDto {
    private String id;
    private String actorUsername;
    private NotificationType type;
    private String entityId;
    private Boolean seen;
    private Instant createdAt;
}
