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
    private final PostUnlikedEventHandler postUnlikedHandler;
    private final CommentCreatedEventHandler commentCreatedHandler;
    private final CommentDeletedEventHandler commentDeletedHandler;
    private final FollowAcceptedEventHandler followAcceptedHandler;
    private final FollowRequestedEventHandler followRequestedHandler;
    private final FollowRejectedEventHandler followRejectedHandler;
    private final UnfollowedEventHandler unfollowedHandler;
    private final PostDeletedEventHandler postDeletedHandler;
    private final StoryDeletedEventHandler storyDeletedHandler;

    /**
     * Register all handlers at startup.
     */
    @PostConstruct
    public void registerHandlers() {
        eventProcessor.registerHandler(EventType.POST_CREATED, postCreatedHandler);
        eventProcessor.registerHandler(EventType.STORY_CREATED, storyCreatedHandler);
        eventProcessor.registerHandler(EventType.POST_LIKED, postLikedHandler);
        eventProcessor.registerHandler(EventType.POST_UNLIKED, postUnlikedHandler);
        eventProcessor.registerHandler(EventType.COMMENT_CREATED, commentCreatedHandler);
        eventProcessor.registerHandler(EventType.COMMENT_DELETED, commentDeletedHandler);
        eventProcessor.registerHandler(EventType.FOLLOW_ACCEPTED, followAcceptedHandler);
        eventProcessor.registerHandler(EventType.FOLLOW_REQUESTED, followRequestedHandler);
        eventProcessor.registerHandler(EventType.FOLLOW_REJECTED, followRejectedHandler);
        eventProcessor.registerHandler(EventType.UNFOLLOWED, unfollowedHandler);
        eventProcessor.registerHandler(EventType.POST_DELETED, postDeletedHandler);
        eventProcessor.registerHandler(EventType.STORY_DELETED, storyDeletedHandler);

        log.info("Event handlers registered successfully");
    }
}
