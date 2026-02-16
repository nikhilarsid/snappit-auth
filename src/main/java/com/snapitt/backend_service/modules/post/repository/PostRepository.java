package com.snapitt.backend_service.modules.post.repository;

import com.snapitt.backend_service.modules.post.model.PostEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PostRepository - Data access layer for posts
 *
 * Query methods:
 * - findById: Get single post by ID
 * - findByAuthorIdOrderByCreatedAtDesc: Get all posts by author (paginated)
 * - findByAuthorIdAndIdLessThanOrderByCreatedAtDesc: Get posts by author with cursor pagination
 */
@Repository
public interface PostRepository extends MongoRepository<PostEntity, String> {
    /**
     * Find all posts by author, sorted by creation date (newest first)
     */
    List<PostEntity> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

    /**
     * Find posts by author with cursor-based pagination
     * Returns posts created before the cursor ID
     */
    List<PostEntity> findByAuthorIdAndIdLessThanOrderByCreatedAtDesc(String authorId, String cursorId, Pageable pageable);
}
