package com.snapitt.backend_service.modules.story.repository;

import com.snapitt.backend_service.modules.story.model.StoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoryRepository extends MongoRepository<StoryEntity, String> {
    
    List<StoryEntity> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(String authorId, Pageable pageable);

    List<StoryEntity> findByAuthorIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(String authorId, String cursorId, Pageable pageable);

    boolean existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(String authorId, Instant now);
}
