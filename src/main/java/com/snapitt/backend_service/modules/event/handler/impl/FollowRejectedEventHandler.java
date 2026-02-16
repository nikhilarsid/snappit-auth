package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRejectedEventHandler implements EventHandler {

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing FOLLOW_REJECTED event: {}", event.getAggregateId());

        String followerId = (String) event.getPayload().get("followerId");
        String followingId = (String) event.getPayload().get("followingId");

        try {
            log.info("Follow request rejected: {} rejected request from {}", followingId, followerId);

            log.info("Successfully processed follow rejection from {} to {}", followerId, followingId);
        } catch (Exception ex) {
            log.error("Error processing FOLLOW_REJECTED event", ex);
            throw ex;
        }
    }
}
