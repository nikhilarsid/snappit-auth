package com.snapitt.backend_service.modules.event.repository;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends MongoRepository<EventEntity, String>, EventRepositoryCustom {

    List<EventEntity> findByStatus(EventStatus status);

    List<EventEntity> findByStatusAndRetryCountGreaterThan(EventStatus status, Integer retryCount);

    @Query("{ 'status': 'processing', 'lockedAt': { '$lt': ?0 } }")
    List<EventEntity> findStaleLocked(Instant staleThreshold);
}
