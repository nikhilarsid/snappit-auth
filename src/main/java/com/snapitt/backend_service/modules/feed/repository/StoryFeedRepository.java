package com.snapitt.backend_service.modules.feed.repository;

import com.snapitt.backend_service.modules.feed.model.StoryFeedEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * StoryFeedRepository - Data access for user's story feed
 *
 * Queries leverage index: { userId: 1, seen: 1, latestStoryAt: -1 }
 * Order priority: unseen first (seen: 1), then latest (latestStoryAt: -1)
 */
public interface StoryFeedRepository extends MongoRepository<StoryFeedEntity, String> {

    /**
     * Get paginated story feed for user (sorted: unseen first, then latest)
     * Filters out deleted stories
     * @param userId Viewer's user ID
     * @param pageable Pagination (first page for no cursor)
     * @return Story creators sorted by seen (false first) then latestStoryAt descending
     */
    List<StoryFeedEntity> findByUserIdAndIsDeletedFalseOrderBySeenAscLatestStoryAtDesc(String userId, Pageable pageable);

    /**
     * Get paginated story feed with cursor (for subsequent pages)
     * Filters out deleted stories
     * @param userId Viewer's user ID
     * @param cursor Previous feed record ID
     * @param pageable Pagination (with limit)
     * @return Story creators after cursor, sorted by seen then latestStoryAt descending
     */
    List<StoryFeedEntity> findByUserIdAndIsDeletedFalseAndIdLessThanOrderBySeenAscLatestStoryAtDesc(String userId, String cursor, Pageable pageable);

    /**
     * Delete all stories from a specific creator from user's feed
     * Used when user unfollows someone - removes all their stories from the feed
     * @param userId The user whose feed should be cleaned
     * @param creatorId The story creator whose entries should be removed
     * @return Number of feed entries deleted
     */
    long deleteByUserIdAndCreatorId(String userId, String creatorId);

    /**
     * Find a specific story feed entry for a user and creator
     * Used to check if the viewer has seen a creator's stories
     */
    java.util.Optional<StoryFeedEntity> findByUserIdAndCreatorIdAndIsDeletedFalse(String userId, String creatorId);
}
