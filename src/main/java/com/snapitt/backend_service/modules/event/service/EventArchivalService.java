package com.snapitt.backend_service.modules.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Archives completed (done) events to AWS S3 as JSON files.
 *
 * <p>S3 key layout:
 * <pre>
 *   {prefix}/{eventType}/YYYY/MM/DD/{eventId}.json
 * </pre>
 * This partitioned layout enables efficient querying with Athena, Spark, or
 * any Hive-compatible engine that supports partition pruning.
 *
 * <p>Called by {@link com.snapitt.backend_service.modules.event.processor.EventProcessor}
 * immediately after an event handler completes successfully and the event is
 * marked as {@code done}.
 */
@Slf4j
@Service
public class EventArchivalService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;
    private final boolean archivalEnabled;
    private final ObjectMapper objectMapper;

    public EventArchivalService(
            S3Client s3Client,
            @Value("${aws.s3.bucket-name:snappit-cdc-events}") String bucketName,
            @Value("${aws.s3.prefix:events}") String prefix,
            @Value("${aws.s3.archival-enabled:true}") boolean archivalEnabled
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.prefix = prefix;
        this.archivalEnabled = archivalEnabled;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Archive a completed event to S3.
     *
     * @param event the event entity that was just marked done
     */
    public void archiveEvent(EventEntity event) {
        if (!archivalEnabled) {
            log.debug("S3 archival disabled — skipping event {}", event.getId());
            return;
        }

        try {
            String key = buildS3Key(event);
            byte[] jsonBytes = serializeEvent(event);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/json")
                    .metadata(Map.of(
                            "event-type", event.getType().name(),
                            "aggregate-id", event.getAggregateId() != null ? event.getAggregateId() : "",
                            "worker-id", event.getLockedBy() != null ? event.getLockedBy() : ""
                    ))
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(jsonBytes));

            log.info("Archived event to S3: s3://{}/{} (type: {}, size: {} bytes)",
                    bucketName, key, event.getType(), jsonBytes.length);

        } catch (Exception e) {
            // Archival failure should NOT break the event processing pipeline.
            // The event is already marked as done in MongoDB — this is a best-effort push.
            log.error("Failed to archive event {} to S3: {}", event.getId(), e.getMessage(), e);
        }
    }

    /**
     * Build a partitioned S3 key.
     *
     * Layout: {prefix}/{EVENT_TYPE}/YYYY/MM/DD/{eventId}.json
     *
     * Using the processedAt timestamp (when the event finished), falling back
     * to createdAt if processedAt is null for some reason.
     */
    private String buildS3Key(EventEntity event) {
        Instant timestamp = event.getProcessedAt() != null ? event.getProcessedAt() : event.getCreatedAt();
        if (timestamp == null) {
            timestamp = Instant.now();
        }

        String datePath = DateTimeFormatter.ofPattern("yyyy/MM/dd")
                .withZone(ZoneOffset.UTC)
                .format(timestamp);

        return String.format("%s/%s/%s/%s.json",
                prefix,
                event.getType().name(),
                datePath,
                event.getId()
        );
    }

    /**
     * Serialize the event into a clean JSON document for archival.
     * Includes all fields plus a metadata block.
     */
    private byte[] serializeEvent(EventEntity event) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("eventId", event.getId());
        doc.put("type", event.getType().name());
        doc.put("aggregateId", event.getAggregateId());
        doc.put("status", event.getStatus().name());
        doc.put("payload", event.getPayload());
        doc.put("retryCount", event.getRetryCount());
        doc.put("createdAt", event.getCreatedAt() != null ? event.getCreatedAt().toString() : null);
        doc.put("processedAt", event.getProcessedAt() != null ? event.getProcessedAt().toString() : null);
        doc.put("lockedBy", event.getLockedBy());
        doc.put("lockedAt", event.getLockedAt() != null ? event.getLockedAt().toString() : null);

        // Archival metadata
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("archivedAt", Instant.now().toString());
        meta.put("archiver", "snappit-cdc-archival-service");
        doc.put("_archivalMetadata", meta);

        return objectMapper.writeValueAsBytes(doc);
    }
}
