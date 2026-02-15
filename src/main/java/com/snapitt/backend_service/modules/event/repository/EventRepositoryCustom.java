package com.snapitt.backend_service.modules.event.repository;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;

import java.time.Instant;
import java.util.Optional;

/**
 * Custom repository methods for complex atomic operations using MongoTemplate.
 */
public interface EventRepositoryCustom {

    /**
     * Atomically claim a pending event for processing.
     * Returns the updated event if successful, or empty if already claimed.
     */
    Optional<EventEntity> claimEvent(String eventId, String workerId, Instant lockedAt);

    /**
     * Release all stale locks for dead worker recovery.
     * Returns the number of events released.
     */
    long releaseStaleLocksForWorkers(Instant staleThreshold);

    /**
     * Mark an event as successfully processed.
     */
    void markEventDone(String eventId, Instant processedAt);

    /**
     * Mark an event as failed or retry it.
     * Increments retry count and sets new status.
     */
    void updateEventRetry(String eventId, EventStatus newStatus);
}
