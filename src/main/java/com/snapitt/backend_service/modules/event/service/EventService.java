package com.snapitt.backend_service.modules.event.service;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.event.model.EventStatus;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

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
}
