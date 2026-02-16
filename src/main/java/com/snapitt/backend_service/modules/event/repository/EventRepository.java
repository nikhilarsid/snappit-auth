package com.snapitt.backend_service.modules.event.repository;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends MongoRepository<EventEntity, String>, EventRepositoryCustom {

    /**
     * Find all pending events waiting for processing.
     */
    List<EventEntity> findByStatus(EventStatus status);

    /**
     * Find events that failed after max retries.
     */
    List<EventEntity> findByStatusAndRetryCountGreaterThan(EventStatus status, Integer retryCount);

    /**
     * Recover stale events locked by dead workers.
     * Finds events locked for more than the specified duration.
     */
    @Query("{ 'status': 'processing', 'lockedAt': { '$lt': ?0 } }")
    List<EventEntity> findStaleLocked(Instant staleThreshold);
}
