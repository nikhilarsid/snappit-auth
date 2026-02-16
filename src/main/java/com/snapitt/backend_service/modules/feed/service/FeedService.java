package com.snapitt.backend_service.modules.feed.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.feed.dto.response.FeedPostDto;
import com.snapitt.backend_service.modules.feed.dto.response.FeedStoryDto;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedPostsResponse;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedStoriesResponse;
import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import com.snapitt.backend_service.modules.feed.model.StoryFeedEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import com.snapitt.backend_service.modules.feed.repository.StoryFeedRepository;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
import com.snapitt.backend_service.modules.post.service.PostService;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.story.dto.response.StoryResponse;
import com.snapitt.backend_service.modules.story.service.StoryService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FeedService - Orchestrates user's feed operations
 *
 * Business Logic:
 * - getMyFeedPosts: Query post_feed collection → Fetch post details → Enrich with creator info
 * - getMyFeedStories: Query story_feed collection → Fetch story details → Enrich with creator info
 *
 * Ordering:
 * - Posts: Unseen first (seen: 1), then fresh (createdAt: -1)
 * - Stories: Unseen first (seen: 1), then latest (latestStoryAt: -1)
 *
 * Pagination: Cursor-based using MongoDB ObjectId
 *
 * Exception Handling:
 * - AuthException: All business logic exceptions with specific error codes
 * - Generic Exception: Caught and wrapped as INTERNAL_SERVER_ERROR
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final Logger logger = LoggerFactory.getLogger(FeedService.class);

    private final PostFeedRepository postFeedRepository;
    private final StoryFeedRepository storyFeedRepository;
    private final PostService postService;
    private final StoryService storyService;
    private final ProfileService profileService;

    /**
     * Get paginated feed posts for authenticated user
     *
     * Flow:
     * 1. Query post_feed collection with pagination (sorted by seen asc, createdAt desc)
     * 2. Extract postIds from feed records
     * 3. Batch fetch post details from PostService
     * 4. Combine feed metadata with post details
     * 5. Return paginated response with nextCursor
     *
     * @param userId Authenticated user ID (from JWT)
     * @param limit Items per page (1-50)
     * @param cursor Opaque cursor for pagination (null for first page)
     * @return PaginatedFeedPostsResponse with posts and nextCursor
     * @throws AuthException (VALIDATION_ERROR, 400) - Invalid limit
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public PaginatedFeedPostsResponse getMyFeedPosts(String userId, int limit, String cursor) {
        try {
            // Validate and normalize limit
            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            // Query post_feed collection
            List<PostFeedEntity> feedRecords;
            if (cursor == null) {
                feedRecords = postFeedRepository.findByUserIdOrderBySeenAscCreatedAtDesc(userId, pageRequest);
            } else {
                feedRecords = postFeedRepository.findByUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(userId, cursor, pageRequest);
            }

            if (feedRecords.isEmpty()) {
                return PaginatedFeedPostsResponse.builder()
                        .data(List.of())
                        .nextCursor(null)
                        .build();
            }

            // Extract post IDs for batch fetch
            List<String> postIds = feedRecords.stream()
                    .map(PostFeedEntity::getPostId)
                    .collect(Collectors.toList());

            // Batch fetch post details from PostService
            // Posts may be deleted (hard delete) - service handles gracefully
            List<PostResponse> posts = postService.getPostsByIds(postIds, userId);

            // Create map for quick lookup by postId
            Map<String, PostResponse> postMap = posts.stream()
                    .collect(Collectors.toMap(PostResponse::getId, p -> p));

            // Combine feed records with post details
            List<FeedPostDto> feedItems = feedRecords.stream()
                    .map(record -> {
                        PostResponse post = postMap.get(record.getPostId());
                        // Skip if post was deleted (post_feed cleanup is handled by background job)
                        if (post == null) {
                            return null;
                        }
                        return FeedPostDto.builder()
                                .id(record.getId())
                                .post(post)
                                .seen(record.getSeen())
                                .createdAt(record.getCreatedAt())
                                .build();
                    })
                    .filter(item -> item != null)  // Filter out deleted posts
                    .collect(Collectors.toList());

            // Prepare nextCursor (last record ID for subsequent queries)
            String nextCursor = feedRecords.isEmpty() ? null : feedRecords.get(feedRecords.size() - 1).getId();

            return PaginatedFeedPostsResponse.builder()
                    .data(feedItems)
                    .nextCursor(nextCursor)
                    .build();

        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving feed posts for user: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving feed posts", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get paginated feed stories for authenticated user
     *
     * Flow:
     * 1. Query story_feed collection with pagination (sorted by seen asc, latestStoryAt desc)
     * 2. Extract creator IDs from feed records
     * 3. Batch fetch creator details (username, avatar) from ProfileService
     * 4. Fetch latest story for each creator from StoryService
     * 5. Combine all details and return paginated response
     *
     * @param userId Authenticated user ID (from JWT)
     * @param limit Items per page (1-50)
     * @param cursor Opaque cursor for pagination (null for first page)
     * @return PaginatedFeedStoriesResponse with stories and nextCursor
     * @throws AuthException (VALIDATION_ERROR, 400) - Invalid limit
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public PaginatedFeedStoriesResponse getMyFeedStories(String userId, int limit, String cursor) {
        try {
            // Validate and normalize limit
            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            // Query story_feed collection (filters out isDeleted = true)
            List<StoryFeedEntity> feedRecords;
            if (cursor == null) {
                feedRecords = storyFeedRepository.findByUserIdAndIsDeletedFalseOrderBySeenAscLatestStoryAtDesc(userId, pageRequest);
            } else {
                feedRecords = storyFeedRepository.findByUserIdAndIsDeletedFalseAndIdLessThanOrderBySeenAscLatestStoryAtDesc(userId, cursor, pageRequest);
            }

            if (feedRecords.isEmpty()) {
                return PaginatedFeedStoriesResponse.builder()
                        .data(List.of())
                        .nextCursor(null)
                        .build();
            }

            // Extract creator IDs for batch fetch
            List<String> creatorIds = feedRecords.stream()
                    .map(StoryFeedEntity::getCreatorId)
                    .collect(Collectors.toList());

            // Batch fetch creator details (username, avatarUrl, etc.)
            List<UserEntity> creators = profileService.getUsersByIds(creatorIds);

            // Create map for quick lookup by creator ID
            Map<String, UserEntity> creatorMap = creators.stream()
                    .collect(Collectors.toMap(UserEntity::getId, c -> c));

            // Build feed items by combining all details
            List<FeedStoryDto> feedItems = feedRecords.stream()
                    .map(record -> {
                        UserEntity creator = creatorMap.get(record.getCreatorId());
                        
                        // Creator should exist (background job ensures only approved followers are in feed)
                        if (creator == null) {
                            logger.warn("Creator not found for story_feed record: {}", record.getId());
                            return null;
                        }

                        // Fetch latest story from creator
                        StoryResponse latestStory = storyService.getLatestStoryByCreator(record.getCreatorId(), userId);

                        // If no active story from creator (expired or no new stories)
                        // Background job should clean up story_feed, but handle gracefully
                        if (latestStory == null) {
                            logger.debug("No active story found for creator: {} in feed", record.getCreatorId());
                            return null;
                        }

                        // Extract avatar from creator's profile
                        String avatarUrl = creator.getProfile() != null ? creator.getProfile().getAvatarUrl() : null;

                        return FeedStoryDto.builder()
                                .id(record.getId())
                                .username(creator.getUsername())
                                .avatar_url(avatarUrl)
                                .story_id(latestStory.getId())
                                .seen(record.getSeen())
                                .latest_story_at(record.getLatestStoryAt())
                                .build();
                    })
                    .filter(item -> item != null)  // Filter out orphaned/expired stories
                    .collect(Collectors.toList());

            // Prepare nextCursor (last record ID for subsequent queries)
            String nextCursor = feedRecords.isEmpty() ? null : feedRecords.get(feedRecords.size() - 1).getId();

            return PaginatedFeedStoriesResponse.builder()
                    .data(feedItems)
                    .nextCursor(nextCursor)
                    .build();

        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving feed stories for user: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving feed stories", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Mark a post as seen in user's feed
     *
     * @param postFeedId Post feed record ID (post_feed._id)
     * @param userId Authenticated user ID (from JWT)
     * @throws AuthException (POST_FEED_NOT_FOUND, 404) - Feed record not found
     * @throws AuthException (FORBIDDEN, 403) - Feed record doesn't belong to user
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public void markPostAsRead(String postFeedId, String userId) {
        try {
            PostFeedEntity feedRecord = postFeedRepository.findById(postFeedId)
                    .orElseThrow(() -> new AuthException("Feed record not found", "POST_FEED_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Verify ownership
            if (!feedRecord.getUserId().equals(userId)) {
                throw new AuthException("This feed record doesn't belong to you", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            // Update seen status
            feedRecord.setSeen(true);
            postFeedRepository.save(feedRecord);

            logger.debug("Marked post feed {} as seen for user {}", postFeedId, userId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error marking post as read: {} for user: {}", postFeedId, userId, ex);
            throw new AuthException("An error occurred while updating post status", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Mark a story as seen in user's feed
     *
     * @param storyFeedId Story feed record ID (story_feed._id)
     * @param userId Authenticated user ID (from JWT)
     * @throws AuthException (STORY_FEED_NOT_FOUND, 404) - Feed record not found
     * @throws AuthException (FORBIDDEN, 403) - Feed record doesn't belong to user
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public void markStoryAsRead(String storyFeedId, String userId) {
        try {
            StoryFeedEntity feedRecord = storyFeedRepository.findById(storyFeedId)
                    .orElseThrow(() -> new AuthException("Feed record not found", "STORY_FEED_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Verify ownership
            if (!feedRecord.getUserId().equals(userId)) {
                throw new AuthException("This feed record doesn't belong to you", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            // Update seen status
            feedRecord.setSeen(true);
            storyFeedRepository.save(feedRecord);

            logger.debug("Marked story feed {} as seen for user {}", storyFeedId, userId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error marking story as read: {} for user: {}", storyFeedId, userId, ex);
            throw new AuthException("An error occurred while updating story status", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
