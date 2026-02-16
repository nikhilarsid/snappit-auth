package com.snapitt.backend_service.modules.notification.dto.response;

import com.snapitt.backend_service.modules.notification.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class NotificationDto {
    private String id;
    private String actorUsername;
    private String actorAvatarUrl;
    private NotificationType type;
    private String entityId;
    private Boolean seen;
    private Instant createdAt;
    private Boolean viewerFollowingActor;
}
