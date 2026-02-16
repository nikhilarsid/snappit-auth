package com.snapitt.backend_service.modules.event.repository;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EventRepositoryCustomImpl implements EventRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<EventEntity> claimEvent(String eventId, String workerId, Instant lockedAt) {
        Query query = new Query(Criteria.where("_id").is(eventId)
                .and("status").is(EventStatus.pending)
                .and("lockedBy").is(null));

        Update update = new Update()
                .set("status", EventStatus.processing)
                .set("lockedBy", workerId)
                .set("lockedAt", lockedAt);

        FindAndModifyOptions options = new FindAndModifyOptions()
                .returnNew(true)
                .upsert(false);

        EventEntity result = mongoTemplate.findAndModify(query, update, options, EventEntity.class);
        return Optional.ofNullable(result);
    }

    @Override
    public long releaseStaleLocksForWorkers(Instant staleThreshold) {
        Query query = new Query(Criteria.where("status").is(EventStatus.processing)
                .and("lockedAt").lt(staleThreshold));

        Update update = new Update()
                .set("status", EventStatus.pending)
                .unset("lockedBy")
                .unset("lockedAt");

        var result = mongoTemplate.updateMulti(query, update, EventEntity.class);
        return result.getModifiedCount();
    }

    @Override
    public void markEventDone(String eventId, Instant processedAt) {
        Query query = new Query(Criteria.where("_id").is(eventId));

        Update update = new Update()
                .set("status", EventStatus.done)
                .set("processedAt", processedAt)
                .unset("lockedBy")
                .unset("lockedAt");

        mongoTemplate.updateFirst(query, update, EventEntity.class);
    }

    @Override
    public void updateEventRetry(String eventId, EventStatus newStatus) {
        Query query = new Query(Criteria.where("_id").is(eventId));

        Update update = new Update()
                .inc("retryCount", 1)
                .set("status", newStatus);

        mongoTemplate.updateFirst(query, update, EventEntity.class);
    }
}
