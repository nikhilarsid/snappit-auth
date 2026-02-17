package com.snapitt.backend_service.modules.notification.websocket;

import com.snapitt.backend_service.modules.notification.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lightweight payload sent over WebSocket when a notification is created or removed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketNotificationPayload {
    private String action;          // "NEW" or "REMOVED"
    private String notificationId;
    private NotificationType type;
    private String actorId;
    private String entityId;
    private Instant createdAt;
}
