package com.snapitt.backend_service.modules.event.service;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    public EventEntity emitEvent(EventType type, String aggregateId, Map<String, Object> payload) {
        EventEntity event = EventEntity.builder()
                .type(type)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(EventStatus.pending)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        return eventRepository.save(event);
    }

    public Optional<EventEntity> claimEvent(String eventId, String workerId) {
        return eventRepository.claimEvent(eventId, workerId, Instant.now());
    }

    public List<EventEntity> getPendingEvents() {
        return eventRepository.findByStatus(EventStatus.pending);
    }

    public void completeEvent(String eventId) {
        eventRepository.markEventDone(eventId, Instant.now());
    }

    public void handleEventFailure(String eventId, int maxRetries) {
        Optional<EventEntity> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return;

        EventEntity event = eventOpt.get();
        int newRetryCount = event.getRetryCount() + 1;

        EventStatus newStatus = newRetryCount >= maxRetries ? EventStatus.failed : EventStatus.pending;
        eventRepository.updateEventRetry(eventId, newStatus);
    }

    public long recoverStaleLockedEvents(long staleThresholdMillis) {
        Instant threshold = Instant.now().minusMillis(staleThresholdMillis);
        return eventRepository.releaseStaleLocksForWorkers(threshold);
    }

    public List<EventEntity> getFailedEvents() {
        return eventRepository.findByStatus(EventStatus.failed);
    }

    public Optional<EventEntity> getEvent(String eventId) {
        return eventRepository.findById(eventId);
    }

    public long countProcessingEvents() {
        return eventRepository.findByStatus(EventStatus.processing).size();
    }
}
