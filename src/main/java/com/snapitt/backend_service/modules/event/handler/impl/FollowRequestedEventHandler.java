package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationType;
import com.snapitt.backend_service.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRequestedEventHandler implements EventHandler {

    private final NotificationRepository notificationRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing FOLLOW_REQUESTED event: {}", event.getAggregateId());

        String followerId = (String) event.getPayload().get("followerId");
        String followingId = (String) event.getPayload().get("followingId");

        try {
            // Create FOLLOW_REQUEST notification for target user
            NotificationEntity notification = NotificationEntity.builder()
                    .targetUserId(followingId)
                    .actorId(followerId)
                    .type(NotificationType.FOLLOW_REQUEST)
                    .entityId(followerId)
                    .seen(false)
                    .createdAt(Instant.now())
                    .build();
            notificationRepository.save(notification);
            log.info("Created FOLLOW_REQUEST notification for user {} from user {}", followingId, followerId);
            log.info("Follow request tracked: {} requested to follow {}", followerId, followingId);
            log.info("Successfully processed follow request from {} to {}", followerId, followingId);
        } catch (Exception ex) {
            log.error("Error processing FOLLOW_REQUESTED event", ex);
            throw ex;
        }
    }
}
