package com.snapitt.backend_service.modules.event.unit;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.repository.EventRepository;
import com.snapitt.backend_service.modules.event.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CDC Event Module - Unit Tests")
public class EventServiceUnitTests {

    private static final Logger log = LoggerFactory.getLogger(EventServiceUnitTests.class);

    @Mock
    private EventRepository eventRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository);
        log.info("Test setUp complete");
    }

    @Test
    @DisplayName("emitEvent: Creates event with pending status")
    void testEmitEvent() {
        
        String postId = "post-123";
        String userId = "user-456";
        Map<String, Object> payload = Map.of("authorId", userId, "content", "Hello!");

        EventEntity expectedEvent = EventEntity.builder()
                .id("event-id-1")
                .type(EventType.POST_CREATED)
                .aggregateId(postId)
                .payload(payload)
                .status(EventStatus.pending)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(eventRepository.save(any(EventEntity.class))).thenReturn(expectedEvent);

        EventEntity emittedEvent = eventService.emitEvent(EventType.POST_CREATED, postId, payload);

        log.info("Testing event emission for POST_CREATED");
        assertNotNull(emittedEvent.getId());
        assertEquals(EventStatus.pending, emittedEvent.getStatus());
        assertEquals(EventType.POST_CREATED, emittedEvent.getType());
        assertEquals(postId, emittedEvent.getAggregateId());
        assertEquals(0, emittedEvent.getRetryCount());
        
        verify(eventRepository, times(1)).save(any(EventEntity.class));
        log.info("✓ Event emitted successfully");
    }

    @Test
    @DisplayName("claimEvent: Returns event when successfully claimed")
    void testClaimEventSuccess() {
        
        String eventId = "event-id-1";
        String workerId = "worker-1";
        
        EventEntity claimedEvent = EventEntity.builder()
                .id(eventId)
                .type(EventType.POST_CREATED)
                .status(EventStatus.processing)
                .lockedBy(workerId)
                .lockedAt(Instant.now())
                .build();

        when(eventRepository.claimEvent(eventId, workerId, any(Instant.class)))
                .thenReturn(Optional.of(claimedEvent));

        var result = eventService.claimEvent(eventId, workerId);

        log.info("Testing atomic event claiming");
        assertTrue(result.isPresent());
        assertEquals(EventStatus.processing, result.get().getStatus());
        assertEquals(workerId, result.get().getLockedBy());
        assertNotNull(result.get().getLockedAt());
        
        verify(eventRepository, times(1)).claimEvent(eq(eventId), eq(workerId), any(Instant.class));
        log.info("✓ Event claimed successfully");
    }

    @Test
    @DisplayName("claimEvent: Returns empty when already claimed")
    void testClaimEventAlreadyClaimed() {
        
        String eventId = "event-id-1";
        String workerId = "worker-2";
        
        when(eventRepository.claimEvent(eventId, workerId, any(Instant.class)))
                .thenReturn(Optional.empty());

        var result = eventService.claimEvent(eventId, workerId);

        log.info("Testing event already claimed scenario");
        assertFalse(result.isPresent());
        
        verify(eventRepository, times(1)).claimEvent(eq(eventId), eq(workerId), any(Instant.class));
        log.info("✓ Already-claimed event correctly rejected");
    }

    @Test
    @DisplayName("completeEvent: Marks event as done")
    void testCompleteEvent() {
        
        String eventId = "event-id-1";

        eventService.completeEvent(eventId);

        log.info("Testing event completion");
        verify(eventRepository, times(1)).markEventDone(eq(eventId), any(Instant.class));
        log.info("✓ Event marked as done");
    }

    @Test
    @DisplayName("handleEventFailure: Increments retry count when below max")
    void testHandleEventFailureWithinLimit() {
        
        String eventId = "event-id-1";
        int maxRetries = 3;

        eventService.handleEventFailure(eventId, maxRetries);

        log.info("Testing event failure handling within retry limit");
        verify(eventRepository, times(1)).updateEventRetry(eq(eventId), eq(EventStatus.pending));
        log.info("✓ Event marked for retry");
    }

    @Test
    @DisplayName("handleEventFailure: Marks as failed when max retries exceeded")
    void testHandleEventFailureExceedsMax() {
        
        String eventId = "event-id-1";
        int maxRetries = 2;

        EventEntity event = EventEntity.builder()
                .id(eventId)
                .retryCount(2)  
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.handleEventFailure(eventId, maxRetries);

        log.info("Testing event failure when max retries exceeded");
        verify(eventRepository, times(1)).updateEventRetry(eq(eventId), eq(EventStatus.failed));
        log.info("✓ Event marked as failed after max retries");
    }

    @Test
    @DisplayName("recoverStaleLockedEvents: Releases stale locks")
    void testRecoverStaleLockedEvents() {
        
        long staleThresholdMs = 300000; 
        long recoveredCount = 3;

        when(eventRepository.releaseStaleLocksForWorkers(any(Instant.class)))
                .thenReturn(recoveredCount);

        long result = eventService.recoverStaleLockedEvents(staleThresholdMs);

        log.info("Testing dead worker recovery");
        assertEquals(recoveredCount, result);
        
        verify(eventRepository, times(1)).releaseStaleLocksForWorkers(any(Instant.class));
        log.info("✓ {} stale locks recovered", recoveredCount);
    }

    @Test
    @DisplayName("getPendingEvents: Retrieves all pending events")
    void testGetPendingEvents() {
        
        EventEntity event1 = EventEntity.builder().id("e1").status(EventStatus.pending).build();
        EventEntity event2 = EventEntity.builder().id("e2").status(EventStatus.pending).build();

        when(eventRepository.findByStatus(EventStatus.pending))
                .thenReturn(java.util.Arrays.asList(event1, event2));

        var pendingEvents = eventService.getPendingEvents();

        log.info("Testing pending events retrieval");
        assertEquals(2, pendingEvents.size());
        
        verify(eventRepository, times(1)).findByStatus(EventStatus.pending);
        log.info("✓ {} pending events retrieved", pendingEvents.size());
    }

    @Test
    @DisplayName("getFailedEvents: Retrieves all failed events")
    void testGetFailedEvents() {
        
        EventEntity failedEvent = EventEntity.builder()
                .id("e1")
                .status(EventStatus.failed)
                .retryCount(5)
                .build();

        when(eventRepository.findByStatus(EventStatus.failed))
                .thenReturn(java.util.Arrays.asList(failedEvent));

        var failedEvents = eventService.getFailedEvents();

        log.info("Testing failed events retrieval");
        assertEquals(1, failedEvents.size());
        assertEquals(EventStatus.failed, failedEvents.get(0).getStatus());
        
        verify(eventRepository, times(1)).findByStatus(EventStatus.failed);
        log.info("✓ Failed events retrieved correctly");
    }

    @Test
    @DisplayName("getEvent: Retrieves event by ID")
    void testGetEvent() {
        
        String eventId = "event-id-1";
        EventEntity event = EventEntity.builder()
                .id(eventId)
                .type(EventType.STORY_CREATED)
                .status(EventStatus.pending)
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        var result = eventService.getEvent(eventId);

        log.info("Testing single event retrieval");
        assertTrue(result.isPresent());
        assertEquals(EventType.STORY_CREATED, result.get().getType());
        
        verify(eventRepository, times(1)).findById(eventId);
        log.info("✓ Event retrieved by ID");
    }

    @Test
    @DisplayName("Multiple event emissions with different types")
    void testMultipleEventTypes() {
        
        EventType[] eventTypes = {
                EventType.POST_CREATED,
                EventType.STORY_CREATED,
                EventType.POST_LIKED,
                EventType.COMMENT_CREATED,
                EventType.FOLLOW_ACCEPTED
        };

        log.info("Testing {} different event types", eventTypes.length);
        for (int i = 0; i < eventTypes.length; i++) {
            EventType eventType = eventTypes[i];
            EventEntity expectedEvent = EventEntity.builder()
                    .id("event-" + i)
                    .type(eventType)
                    .status(EventStatus.pending)
                    .build();

            when(eventRepository.save(any(EventEntity.class))).thenReturn(expectedEvent);

            EventEntity emitted = eventService.emitEvent(eventType, "agg-" + i, new HashMap<>());
            
            assertEquals(eventType, emitted.getType());
            assertEquals(EventStatus.pending, emitted.getStatus());
            log.info("✓ {} event created", eventType);
        }

        log.info("✓ All event types tested successfully");
    }

    @Test
    @DisplayName("Event lifecycle: Emit -> Claim -> Complete")
    void testCompleteEventLifecycle() {
        
        String eventId = "event-complete-lifecycle";
        String workerId = "worker-1";
        Map<String, Object> payload = Map.of("data", "value");

        EventEntity emittedEvent = EventEntity.builder()
                .id(eventId)
                .type(EventType.POST_CREATED)
                .status(EventStatus.pending)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        EventEntity claimedEvent = EventEntity.builder()
                .id(eventId)
                .type(EventType.POST_CREATED)
                .status(EventStatus.processing)
                .lockedBy(workerId)
                .lockedAt(Instant.now())
                .build();

        when(eventRepository.save(any(EventEntity.class))).thenReturn(emittedEvent);
        when(eventRepository.claimEvent(eventId, workerId, any(Instant.class)))
                .thenReturn(Optional.of(claimedEvent));

        log.info("Testing complete event lifecycle");
        
        EventEntity emitted = eventService.emitEvent(EventType.POST_CREATED, eventId, payload);
        assertEquals(EventStatus.pending, emitted.getStatus());
        log.info("1. Event emitted - pending state");

        var claimed = eventService.claimEvent(eventId, workerId);
        assertTrue(claimed.isPresent());
        assertEquals(EventStatus.processing, claimed.get().getStatus());
        log.info("2. Event claimed - processing state");

        eventService.completeEvent(eventId);
        verify(eventRepository).markEventDone(eq(eventId), any(Instant.class));
        log.info("3. Event completed - done state");

        verify(eventRepository).save(any(EventEntity.class));
        verify(eventRepository).claimEvent(eq(eventId), eq(workerId), any(Instant.class));
        verify(eventRepository).markEventDone(eq(eventId), any(Instant.class));
        
        log.info("✓ Complete lifecycle tested successfully");
    }
}
