package com.snapitt.backend_service.modules.event.config;

import com.snapitt.backend_service.modules.event.handler.impl.*;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.processor.EventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Event Handler Configuration.
 * Registers all event handlers with the processor at application startup.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventHandlerConfiguration {

    private final EventProcessor eventProcessor;
    private final PostCreatedEventHandler postCreatedHandler;
    private final StoryCreatedEventHandler storyCreatedHandler;
    private final PostLikedEventHandler postLikedHandler;
    private final CommentCreatedEventHandler commentCreatedHandler;
    private final FollowAcceptedEventHandler followAcceptedHandler;

    /**
     * Register all handlers at startup.
     */
    @PostConstruct
    public void registerHandlers() {
        eventProcessor.registerHandler(EventType.POST_CREATED, postCreatedHandler);
        eventProcessor.registerHandler(EventType.STORY_CREATED, storyCreatedHandler);
        eventProcessor.registerHandler(EventType.POST_LIKED, postLikedHandler);
        eventProcessor.registerHandler(EventType.COMMENT_CREATED, commentCreatedHandler);
        eventProcessor.registerHandler(EventType.FOLLOW_ACCEPTED, followAcceptedHandler);

        log.info("Event handlers registered successfully");
    }
}
