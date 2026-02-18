package com.snapitt.backend_service.modules.event.processor;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.service.EventArchivalService;
import com.snapitt.backend_service.modules.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventProcessor {

    private final EventService eventService;
    private final EventArchivalService eventArchivalService;
    private final Map<EventType, EventHandler> handlers = new HashMap<>();

    public void registerHandler(EventType eventType, EventHandler handler) {
        handlers.put(eventType, handler);
        log.info("Registered handler for event type: {}", eventType);
    }

    public void processEvent(EventEntity event, int maxRetries) {
        try {
            EventHandler handler = handlers.get(event.getType());
            if (handler == null) {
                log.warn("No handler registered for event type: {}", event.getType());
                eventService.completeEvent(event.getId());
                archiveCompletedEvent(event);
                return;
            }

            handler.handle(event);
            eventService.completeEvent(event.getId());
            log.info("Successfully processed event: {} (type: {})", event.getId(), event.getType());

            // Archive the completed event to S3 (best-effort, won't break the pipeline)
            archiveCompletedEvent(event);

        } catch (Exception e) {
            log.error("Error processing event: {} (type: {}), retry count: {}", 
                    event.getId(), event.getType(), event.getRetryCount(), e);
            eventService.handleEventFailure(event.getId(), maxRetries);
        }
    }

    /**
     * Push a completed (done) event to S3 for long-term archival.
     * This is fire-and-forget — failures are logged but do not affect event processing.
     */
    private void archiveCompletedEvent(EventEntity event) {
        try {
            // Re-fetch to get the updated processedAt timestamp set by completeEvent
            eventService.getEvent(event.getId()).ifPresent(eventArchivalService::archiveEvent);
        } catch (Exception e) {
            log.error("Failed to archive event {} to S3 (non-fatal): {}", event.getId(), e.getMessage());
        }
    }

    public EventHandler getHandler(EventType eventType) {
        return handlers.get(eventType);
    }

    public boolean hasHandler(EventType eventType) {
        return handlers.containsKey(eventType);
    }
}
