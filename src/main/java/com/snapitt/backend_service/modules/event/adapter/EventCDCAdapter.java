package com.snapitt.backend_service.modules.event.adapter;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.processor.EventProcessor;
import com.snapitt.backend_service.modules.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CDC (Change Data Capture) Adapter using MongoDB Change Streams.
 * 
 * This component:
 * 1. Watches the 'events' collection for new inserts
 * 2. Routes each event to the EventProcessor
 * 3. Handles event claiming, processing, and completion
 * 
 * This is the producer side of the transactional outbox pattern.
 * It operates independently and doesn't modify business data - only handles
 * event distribution to workers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventCDCAdapter {

    private final MongoTemplate mongoTemplate;
    private final MongoClient mongoClient;
    private final EventProcessor eventProcessor;
    private final EventService eventService;

    @Value("${snapitt.event.db-name:snappit}")
    private String databaseName;

    @Value("${snapitt.event.max-retries:5}")
    private int maxRetries;

    @Value("${snapitt.event.worker-id:worker-1}")
    private String workerId;

    private ExecutorService changeStreamExecutor;

    /**
     * Initialize the change stream listener.
     * Runs in a separate thread to avoid blocking the main application.
     */
    @PostConstruct
    public void initializeChangeStreamListener() {
        changeStreamExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "EventCDC-ChangeStream");
            thread.setDaemon(true);
            return thread;
        });

        changeStreamExecutor.submit(this::watchEventsCollection);
        log.info("Event CDC Adapter initialized with worker ID: {}", workerId);
    }

    /**
     * Watch the 'events' collection for insert operations.
     * This runs indefinitely and processes each new event.
     */
    private void watchEventsCollection() {
        try {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            var changeStream = database.getCollection("events")
                    .watch(Arrays.asList(
                            new Document("$match", 
                                    new Document("operationType", "insert")
                            )
                    ));

            log.info("Change stream listener started for 'events' collection");

            for (ChangeStreamDocument<Document> change : changeStream) {
                try {
                    Document fullDocument = change.getFullDocument();
                    if (fullDocument != null) {
                        String eventId = fullDocument.getObjectId("_id").toString();
                        log.debug("New event detected via change stream: {}", eventId);
                        
                        // Try to claim the event
                        processEventIfClaimed(eventId);
                    }
                } catch (Exception e) {
                    log.error("Error processing change stream event", e);
                }
            }
        } catch (Exception e) {
            log.error("Change stream interrupted", e);
            // Reconnect after delay
            try {
                Thread.sleep(5000);
                watchEventsCollection();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Try to claim an event and process it if successful.
     */
    private void processEventIfClaimed(String eventId) {
        var claimedEvent = eventService.claimEvent(eventId, workerId);
        
        if (claimedEvent.isPresent()) {
            EventEntity event = claimedEvent.get();
            log.info("Event claimed by worker {}: {} (type: {})", 
                    workerId, eventId, event.getType());
            
            // Process the event
            eventProcessor.processEvent(event, maxRetries);
        } else {
            log.debug("Event {} already claimed by another worker", eventId);
        }
    }

    /**
     * Gracefully shutdown the change stream listener.
     */
    public void shutdown() {
        if (changeStreamExecutor != null) {
            changeStreamExecutor.shutdown();
            log.info("Event CDC Adapter shutdown initiated");
        }
    }
}
