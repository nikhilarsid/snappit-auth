package com.snapitt.backend_service.modules.story.repository;

import com.snapitt.backend_service.modules.story.model.StoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StoryRepository - Data access layer for stories
 *
 * Query methods:
 * - findById: Get single story by ID
 * - findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc: Get all active stories by author (paginated)
 * - findByAuthorIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc: Get active stories by author with cursor pagination
 * - existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan: Check if user has any active (non-expired) stories
 */
@Repository
public interface StoryRepository extends MongoRepository<StoryEntity, String> {
    /**
     * Find all active stories by author, sorted by creation date (newest first)
     */
    List<StoryEntity> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(String authorId, Pageable pageable);

    /**
     * Find active stories by author with cursor-based pagination
     * Returns stories created before the cursor ID
     */
    List<StoryEntity> findByAuthorIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(String authorId, String cursorId, Pageable pageable);

    /**
     * Check if user has any active, non-expired stories
     */
    boolean existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(String authorId, Instant now);
}
