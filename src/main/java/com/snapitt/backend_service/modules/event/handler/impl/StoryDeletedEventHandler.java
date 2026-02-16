package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
            
            log.info("Successfully processed deletion of story {} by creator {}", storyId, creatorId);
        } catch (Exception ex) {
            log.error("Error processing STORY_DELETED event for story {}", storyId, ex);
            throw ex;
        }
    }
}
