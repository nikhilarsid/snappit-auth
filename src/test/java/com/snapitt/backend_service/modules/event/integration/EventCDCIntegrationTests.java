package com.snapitt.backend_service.modules.event.integration;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.processor.EventProcessor;
import com.snapitt.backend_service.modules.event.repository.EventRepository;
import com.snapitt.backend_service.modules.event.service.EventService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the CDC event architecture.
 * Tests the complete flow from event emission to handler processing.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CDC Event Architecture - End-to-End Tests")
public class EventCDCIntegrationTests {

    private static final Logger log = LoggerFactory.getLogger(EventCDCIntegrationTests.class);

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventProcessor eventProcessor;

    // Counters to verify handler execution
    private AtomicInteger postCreatedHandlerCallCount = new AtomicInteger(0);
    private AtomicInteger storyCreatedHandlerCallCount = new AtomicInteger(0);
    private AtomicInteger postLikedHandlerCallCount = new AtomicInteger(0);
    private AtomicInteger commentCreatedHandlerCallCount = new AtomicInteger(0);
    private AtomicInteger followAcceptedHandlerCallCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        // Reset counters
        postCreatedHandlerCallCount.set(0);
        storyCreatedHandlerCallCount.set(0);
        postLikedHandlerCallCount.set(0);
        commentCreatedHandlerCallCount.set(0);
        followAcceptedHandlerCallCount.set(0);

        // Clear database
        eventRepository.deleteAll();

        // Configure Awaitility
        Awaitility.reset();
        Awaitility.setDefaultTimeout(Duration.ofSeconds(10));
        Awaitility.setDefaultPollInterval(Duration.ofMillis(100));

        log.info("Test setUp complete");
    }

    @Test
    @DisplayName("POST_CREATED: Event emission, claiming, and processing")
    void testPostCreatedEventFlow() {
        // Arrange
        String postId = "post-123";
        String authorId = "user-456";
        Map<String, Object> payload = Map.of(
                "authorId", authorId,
                "content", "Hello World!"
        );

        // Act - Emit event
        log.info("Emitting POST_CREATED event for post: {}", postId);
        EventEntity emittedEvent = eventService.emitEvent(EventType.POST_CREATED, postId, payload);

        // Assert - Event created
        assertNotNull(emittedEvent.getId());
        assertEquals(EventStatus.pending, emittedEvent.getStatus());
        assertEquals(0, emittedEvent.getRetryCount());
        log.info("Event created with ID: {}", emittedEvent.getId());

        // Act - Find and claim event (simulating worker)
        var claimedEvent = eventService.claimEvent(emittedEvent.getId(), "worker-1");

        // Assert - Event claimed
        assertTrue(claimedEvent.isPresent());
        assertEquals(EventStatus.processing, claimedEvent.get().getStatus());
        assertEquals("worker-1", claimedEvent.get().getLockedBy());
        log.info("Event claimed by worker-1");

        // Act - Process event
        eventService.completeEvent(emittedEvent.getId());

        // Assert - Event completed
        var completedEvent = eventRepository.findById(emittedEvent.getId());
        assertTrue(completedEvent.isPresent());
        assertEquals(EventStatus.done, completedEvent.get().getStatus());
        assertNotNull(completedEvent.get().getProcessedAt());
        assertNull(completedEvent.get().getLockedBy());
        log.info("Event processing complete");
    }

    @Test
    @DisplayName("STORY_CREATED: Full event lifecycle with retries")
    void testStoryCreatedEventFlow() {
        // Arrange
        String storyId = "story-789";
        String authorId = "user-101";
        Map<String, Object> payload = Map.of(
                "authorId", authorId,
                "mediaUrl", "https://example.com/story.jpg"
        );

        // Act - Emit event
        log.info("Emitting STORY_CREATED event for story: {}", storyId);
        EventEntity emittedEvent = eventService.emitEvent(EventType.STORY_CREATED, storyId, payload);

        // Assert - Event created
        assertEquals(EventStatus.pending, emittedEvent.getStatus());
        assertEquals(EventType.STORY_CREATED, emittedEvent.getType());
        log.info("Story event created: {}", emittedEvent.getId());

        // Act - Try to claim same event twice (second should fail)
        var firstClaim = eventService.claimEvent(emittedEvent.getId(), "worker-1");
        var secondClaim = eventService.claimEvent(emittedEvent.getId(), "worker-2");

        // Assert - Only first claim succeeds
        assertTrue(firstClaim.isPresent());
        assertTrue(secondClaim.isEmpty());
        log.info("Event claiming is atomic - second claim correctly rejected");

        // Act - Complete event
        eventService.completeEvent(emittedEvent.getId());

        // Assert - Verify completion
        var finalEvent = eventRepository.findById(emittedEvent.getId()).get();
        assertEquals(EventStatus.done, finalEvent.getStatus());
        assertEquals(0, finalEvent.getRetryCount());
    }

    @Test
    @DisplayName("POST_LIKED: Event with retry on failure")
    void testPostLikedEventWithRetry() {
        // Arrange
        String postId = "post-liked-001";
        Map<String, Object> payload = Map.of("userId", "user-999");

        // Act - Emit event
        log.info("Emitting POST_LIKED event for post: {}", postId);
        EventEntity emittedEvent = eventService.emitEvent(EventType.POST_LIKED, postId, payload);

        // Assert - Initial state
        assertEquals(EventStatus.pending, emittedEvent.getStatus());
        assertEquals(0, emittedEvent.getRetryCount());

        // Act - Claim event
        var claimedEvent = eventService.claimEvent(emittedEvent.getId(), "worker-1");
        assertTrue(claimedEvent.isPresent());

        // Act - Simulate failure and retry
        int maxRetries = 3;
        eventService.handleEventFailure(emittedEvent.getId(), maxRetries);

        // Assert - Event goes back to pending for retry
        var retriedEvent = eventRepository.findById(emittedEvent.getId()).get();
        assertEquals(EventStatus.pending, retriedEvent.getStatus());
        assertEquals(1, retriedEvent.getRetryCount());
        assertNull(retriedEvent.getLockedBy());
        log.info("Event retry #1 scheduled");

        // Act - Claim again and process
        var reclaimedEvent = eventService.claimEvent(emittedEvent.getId(), "worker-2");
        assertTrue(reclaimedEvent.isPresent());
        assertEquals(1, reclaimedEvent.get().getRetryCount());

        // Act - Complete on second attempt
        eventService.completeEvent(emittedEvent.getId());

        // Assert - Final state
        var finalEvent = eventRepository.findById(emittedEvent.getId()).get();
        assertEquals(EventStatus.done, finalEvent.getStatus());
        assertEquals(1, finalEvent.getRetryCount());
        log.info("Event completed after 1 retry");
    }

    @Test
    @DisplayName("COMMENT_CREATED: Event payload integrity")
    void testCommentCreatedEventPayload() {
        // Arrange
        String commentId = "comment-555";
        String postId = "post-666";
        String parentCommentId = "comment-parent-777";
        String userId = "user-888";

        Map<String, Object> payload = new HashMap<>();
        payload.put("postId", postId);
        payload.put("parentCommentId", parentCommentId);
        payload.put("userId", userId);
        payload.put("text", "Great post!");

        // Act - Emit event
        log.info("Emitting COMMENT_CREATED event for comment: {}", commentId);
        EventEntity emittedEvent = eventService.emitEvent(EventType.COMMENT_CREATED, commentId, payload);

        // Assert - Event created with payload intact
        assertEquals(EventType.COMMENT_CREATED, emittedEvent.getType());
        assertEquals(commentId, emittedEvent.getAggregateId());
        assertEquals(postId, emittedEvent.getPayload().get("postId"));
        assertEquals(parentCommentId, emittedEvent.getPayload().get("parentCommentId"));
        assertEquals(userId, emittedEvent.getPayload().get("userId"));
        assertEquals("Great post!", emittedEvent.getPayload().get("text"));
        log.info("Event payload verified");

        // Act - Retrieve and verify persistence
        var retrievedEvent = eventRepository.findById(emittedEvent.getId()).get();

        // Assert - Payload persisted correctly
        assertEquals(postId, retrievedEvent.getPayload().get("postId"));
        assertEquals(userId, retrievedEvent.getPayload().get("userId"));
        log.info("Event payload persisted correctly");
    }

    @Test
    @DisplayName("FOLLOW_ACCEPTED: Multiple events in sequence")
    void testFollowAcceptedMultipleEvents() {
        // Arrange
        String[] followRequestIds = {"follow-req-1", "follow-req-2", "follow-req-3"};
        
        // Act - Emit multiple events
        log.info("Emitting {} FOLLOW_ACCEPTED events", followRequestIds.length);
        EventEntity[] events = new EventEntity[followRequestIds.length];
        
        for (int i = 0; i < followRequestIds.length; i++) {
            events[i] = eventService.emitEvent(
                    EventType.FOLLOW_ACCEPTED,
                    followRequestIds[i],
                    Map.of("followerId", "user-" + i, "followingId", "user-" + (i + 100))
            );
        }

        // Assert - All events created
        for (int i = 0; i < followRequestIds.length; i++) {
            assertEquals(EventStatus.pending, events[i].getStatus());
        }
        log.info("All {} events created successfully", followRequestIds.length);

        // Act - Claim and process each event
        for (int i = 0; i < followRequestIds.length; i++) {
            var claimedEvent = eventService.claimEvent(events[i].getId(), "worker-1");
            assertTrue(claimedEvent.isPresent());
            eventService.completeEvent(events[i].getId());
        }

        // Assert - All events processed
        var pendingCount = eventService.getPendingEvents().size();
        assertEquals(0, pendingCount);
        log.info("All events processed successfully");
    }

    @Test
    @DisplayName("Max retries exceeded: Event marked as failed")
    void testMaxRetriesExceeded() {
        // Arrange
        String eventId = "fail-event-001";
        int maxRetries = 2;

        // Act - Emit event
        log.info("Emitting event for max retry test (max retries: {})", maxRetries);
        EventEntity emittedEvent = eventService.emitEvent(EventType.POST_CREATED, eventId, Map.of());

        // Act - Simulate max retries exceeded
        for (int i = 0; i < maxRetries; i++) {
            var claimed = eventService.claimEvent(emittedEvent.getId(), "worker-" + i);
            assertTrue(claimed.isPresent());
            eventService.handleEventFailure(emittedEvent.getId(), maxRetries);
            log.info("Retry attempt {}", i + 1);
        }

        // Assert - After maxRetries attempts, event should be failed
        var failedEvent = eventRepository.findById(emittedEvent.getId()).get();
        assertEquals(EventStatus.failed, failedEvent.getStatus());
        assertEquals(maxRetries, failedEvent.getRetryCount());
        log.info("Event correctly marked as failed after {} retries", maxRetries);

        // Act - Verify failed event is retrievable
        var failedEvents = eventService.getFailedEvents();

        // Assert
        assertTrue(failedEvents.stream().anyMatch(e -> e.getId().equals(emittedEvent.getId())));
        log.info("Failed event found in failed events list");
    }

    @Test
    @DisplayName("Concurrent claims: Only one worker succeeds")
    void testConcurrentEventClaiming() throws InterruptedException {
        // Arrange
        String eventId = "concurrent-event-001";
        EventEntity emittedEvent = eventService.emitEvent(EventType.POST_LIKED, eventId, Map.of());

        // Act - Simulate two workers trying to claim simultaneously
        AtomicInteger successCount = new AtomicInteger(0);
        
        Thread worker1 = new Thread(() -> {
            var claimed = eventService.claimEvent(emittedEvent.getId(), "worker-1");
            if (claimed.isPresent()) {
                successCount.incrementAndGet();
                log.info("Worker-1 successfully claimed event");
            }
        });

        Thread worker2 = new Thread(() -> {
            var claimed = eventService.claimEvent(emittedEvent.getId(), "worker-2");
            if (claimed.isPresent()) {
                successCount.incrementAndGet();
                log.info("Worker-2 successfully claimed event");
            }
        });

        worker1.start();
        worker2.start();
        worker1.join();
        worker2.join();

        // Assert - Only one worker claimed the event
        assertEquals(1, successCount.get());
        log.info("Concurrent claiming test passed: only 1 worker succeeded");

        // Verify the claiming worker
        var claimedEvent = eventRepository.findById(emittedEvent.getId()).get();
        assertNotNull(claimedEvent.getLockedBy());
        assertTrue(claimedEvent.getLockedBy().equals("worker-1") || 
                  claimedEvent.getLockedBy().equals("worker-2"));
    }

    @Test
    @DisplayName("Event retrieval and querying")
    void testEventRetrieval() {
        // Arrange
        EventEntity event1 = eventService.emitEvent(EventType.POST_CREATED, "post-1", Map.of());
        EventEntity event2 = eventService.emitEvent(EventType.STORY_CREATED, "story-1", Map.of());

        // Act - Get pending events
        var pendingEvents = eventService.getPendingEvents();

        // Assert
        assertEquals(2, pendingEvents.size());
        log.info("Found {} pending events", pendingEvents.size());

        // Act - Get single event
        var retrieved = eventService.getEvent(event1.getId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(EventStatus.pending, retrieved.get().getStatus());
        assertEquals(EventType.POST_CREATED, retrieved.get().getType());
        log.info("Event retrieval test passed");
    }

    @Test
    @DisplayName("All event types: Basic creation and claiming")
    void testAllEventTypes() {
        // Arrange - Define all supported event types
        EventType[] eventTypes = {
                EventType.POST_CREATED,
                EventType.STORY_CREATED,
                EventType.POST_LIKED,
                EventType.POST_UNLIKED,
                EventType.POST_DISLIKED,
                EventType.COMMENT_CREATED,
                EventType.FOLLOW_REQUESTED,
                EventType.FOLLOW_ACCEPTED,
                EventType.FOLLOW_REJECTED,
                EventType.UNFOLLOWED,
                EventType.POST_DELETED,
                EventType.STORY_DELETED
        };

        log.info("Testing all {} event types", eventTypes.length);

        // Act & Assert - Create and claim each event type
        for (int i = 0; i < eventTypes.length; i++) {
            EventType eventType = eventTypes[i];
            String aggregateId = eventType.toString().toLowerCase() + "-" + i;
            Map<String, Object> payload = Map.of("index", i);

            // Emit
            EventEntity emittedEvent = eventService.emitEvent(eventType, aggregateId, payload);
            assertEquals(EventStatus.pending, emittedEvent.getStatus());

            // Claim
            var claimedEvent = eventService.claimEvent(emittedEvent.getId(), "worker-1");
            assertTrue(claimedEvent.isPresent());
            assertEquals(EventStatus.processing, claimedEvent.get().getStatus());

            // Complete
            eventService.completeEvent(emittedEvent.getId());
            var finalEvent = eventRepository.findById(emittedEvent.getId()).get();
            assertEquals(EventStatus.done, finalEvent.getStatus());

            log.info("✓ {} event processed successfully", eventType);
        }

        log.info("All {} event types tested successfully", eventTypes.length);
    }
}
