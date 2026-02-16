package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationType;
import com.snapitt.backend_service.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRequestWithdrawnEventHandler implements EventHandler {

    private final NotificationRepository notificationRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing FOLLOW_REQUEST_WITHDRAWN event: {}", event.getAggregateId());

        String followerId = (String) event.getPayload().get("followerId");
        String followingId = (String) event.getPayload().get("followingId");

        try {
            notificationRepository.deleteByTargetUserIdAndActorIdAndType(
                    followingId, followerId, NotificationType.FOLLOW_REQUEST);
            log.info("Removed FOLLOW_REQUEST notification for user {} from user {}", followingId, followerId);
            log.info("Successfully processed follow request withdrawal from {} to {}", followerId, followingId);
        } catch (Exception ex) {
            log.error("Error processing FOLLOW_REQUEST_WITHDRAWN event", ex);
            throw ex;
        }
    }
}
