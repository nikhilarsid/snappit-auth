package com.snapitt.backend_service.modules.post.repository;

import com.snapitt.backend_service.modules.post.model.LikeEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * LikeRepository - Data access layer for post likes
 *
 * Query methods:
 * - findByUserIdAndPostId: Check if user has liked a post
 * - deleteByUserIdAndPostId: Remove like record
 */
@Repository
public interface LikeRepository extends MongoRepository<LikeEntity, String> {
    /**
     * Find a like record by user and post
     */
    Optional<LikeEntity> findByUserIdAndPostId(String userId, String postId);

    /**
     * Delete a like record by user and post
     */
    void deleteByUserIdAndPostId(String userId, String postId);

    /**
     * Count likes for a post
     */
    long countByPostId(String postId);
}
