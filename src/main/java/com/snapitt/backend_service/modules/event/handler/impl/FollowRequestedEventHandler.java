package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles FOLLOW_REQUESTED event.
 * 
 * When a user sends a follow request:
 * 1. Log the event for tracking
 * 2. TODO: Send notification to the target user
 * 3. Mark event as done
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRequestedEventHandler implements EventHandler {

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing FOLLOW_REQUESTED event: {}", event.getAggregateId());

        String followerId = (String) event.getPayload().get("followerId");
        String followingId = (String) event.getPayload().get("followingId");

        try {
            log.info("Follow request tracked: {} requested to follow {}", followerId, followingId);

            // TODO: Send notification to followingId about the follow request
            // - Create notification document
            // - Insert into notifications collection
            // - This will trigger a separate notification delivery system

            log.info("Successfully processed follow request from {} to {}", followerId, followingId);
        } catch (Exception ex) {
            log.error("Error processing FOLLOW_REQUESTED event", ex);
            throw ex;
        }
    }
}
