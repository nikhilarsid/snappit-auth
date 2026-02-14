package com.snapitt.backend_service.modules.event.repository;

import com.snapitt.backend_service.modules.event.model.EventEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRepository extends MongoRepository<EventEntity, String> {
}
