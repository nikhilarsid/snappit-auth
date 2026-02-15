package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles STORY_DELETED event.
 * 
 * When a story is deleted:
 * 1. Story is marked as deleted in stories collection (hard delete or soft delete flag)
 * 2. Queries filter deleted stories at read time
 * 3. No feed cleanup needed (story_feed tracks creators, not individual stories)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoryDeletedEventHandler implements EventHandler {

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing STORY_DELETED event: {}", event.getAggregateId());

        String storyId = (String) event.getPayload().get("storyId");
        String creatorId = (String) event.getPayload().get("creatorId");

        try {
            // Note: Story deletion is handled at query time by filtering deleted stories
            // The story_feed schema tracks creators, not individual stories
            // Queries already filter out stories marked as deleted in the stories collection
            // So no feed cleanup is needed here
            log.info("Successfully processed deletion of story {} by creator {}", storyId, creatorId);
        } catch (Exception ex) {
            log.error("Error processing STORY_DELETED event for story {}", storyId, ex);
            throw ex;
        }
    }
}
