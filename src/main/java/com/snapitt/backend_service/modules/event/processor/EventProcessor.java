package com.snapitt.backend_service.modules.event.processor;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes events to appropriate handlers based on event type.
 * This is the central processor for the CDC pattern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventProcessor {

    private final EventService eventService;
    private final Map<EventType, EventHandler> handlers = new HashMap<>();

    /**
     * Register a handler for a specific event type.
     */
    public void registerHandler(EventType eventType, EventHandler handler) {
        handlers.put(eventType, handler);
        log.info("Registered handler for event type: {}", eventType);
    }

    /**
     * Process an event by routing it to the appropriate handler.
     * Handles retries and error logging.
     */
    public void processEvent(EventEntity event, int maxRetries) {
        try {
            EventHandler handler = handlers.get(event.getType());
            if (handler == null) {
                log.warn("No handler registered for event type: {}", event.getType());
                eventService.completeEvent(event.getId());
                return;
            }

            handler.handle(event);
            eventService.completeEvent(event.getId());
            log.info("Successfully processed event: {} (type: {})", event.getId(), event.getType());

        } catch (Exception e) {
            log.error("Error processing event: {} (type: {}), retry count: {}", 
                    event.getId(), event.getType(), event.getRetryCount(), e);
            eventService.handleEventFailure(event.getId(), maxRetries);
        }
    }

    /**
     * Get handler for a specific event type.
     */
    public EventHandler getHandler(EventType eventType) {
        return handlers.get(eventType);
    }

    /**
     * Check if handler exists for event type.
     */
    public boolean hasHandler(EventType eventType) {
        return handlers.containsKey(eventType);
    }
}
