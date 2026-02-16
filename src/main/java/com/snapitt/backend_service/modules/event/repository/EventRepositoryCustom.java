package com.snapitt.backend_service.modules.event.repository;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;

import java.time.Instant;
import java.util.Optional;

public interface EventRepositoryCustom {

    Optional<EventEntity> claimEvent(String eventId, String workerId, Instant lockedAt);

    long releaseStaleLocksForWorkers(Instant staleThreshold);

    void markEventDone(String eventId, Instant processedAt);

    void updateEventRetry(String eventId, EventStatus newStatus);
}
