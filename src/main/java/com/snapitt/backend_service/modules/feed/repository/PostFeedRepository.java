package com.snapitt.backend_service.modules.feed.repository;

import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * PostFeedRepository - Data access for user's post feed
 *
 * Queries leverage index: { userId: 1, seen: 1, createdAt: -1 }
 * Order priority: unseen first (seen: 1), then freshest (createdAt: -1)
 */
public interface PostFeedRepository extends MongoRepository<PostFeedEntity, String> {

    /**
     * Get paginated post feed for user (sorted: unseen first, then fresh)
     * @param userId Viewer's user ID
     * @param pageable Pagination (first page for no cursor)
     * @return Posts sorted by seen (false first) then createdAt descending
     */
    List<PostFeedEntity> findByUserIdOrderBySeenAscCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Get paginated post feed with cursor (for subsequent pages)
     * @param userId Viewer's user ID
     * @param cursor Previous feed record ID
     * @param pageable Pagination (with limit)
     * @return Posts after cursor, sorted by seen then createdAt descending
     */
    List<PostFeedEntity> findByUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(String userId, String cursor, Pageable pageable);

    /**
     * Delete all posts from a specific author from user's feed
     * Used when user unfollows someone - removes all their posts from the feed
     * @param userId The user whose feed should be cleaned
     * @param authorId The author whose posts should be removed
     * @return Number of feed entries deleted
     */
    long deleteByUserIdAndAuthorId(String userId, String authorId);

    /**
     * Delete a specific post from all users' feeds
     * Used when a post is deleted - removes it from all feed collections
     * @param postId The post ID to delete
     * @return Number of feed entries deleted
     */
    long deleteByPostId(String postId);
}
