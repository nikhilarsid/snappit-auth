package com.snapitt.backend_service.modules.event.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@Document(collection = "events")
public class EventEntity {
    @Id
    private String id;

    private EventType type;

    private String aggregateId;

    private Map<String, Object> payload;

    private EventStatus status;

    private Integer retryCount;

    private Instant createdAt;

    private Instant processedAt;

    private String lockedBy;

    private Instant lockedAt;
}
