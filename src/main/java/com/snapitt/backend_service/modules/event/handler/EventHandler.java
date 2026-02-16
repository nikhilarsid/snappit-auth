package com.snapitt.backend_service.modules.event.handler;

import com.snapitt.backend_service.modules.event.model.EventEntity;

/**
 * Base interface for event handlers.
 * Each event type (POST_CREATED, STORY_CREATED, etc.) has a corresponding handler.
 */
public interface EventHandler {

    /**
     * Process the event.
     * Should apply all business mutations within a MongoDB transaction.
     *
     * @param event The event to process
     * @throws Exception if processing fails (will trigger retry)
     */
    void handle(EventEntity event) throws Exception;
}
