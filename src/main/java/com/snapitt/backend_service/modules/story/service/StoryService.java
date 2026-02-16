package com.snapitt.backend_service.modules.story.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.story.dto.request.CreateStoryRequest;
import com.snapitt.backend_service.modules.story.dto.response.PaginatedStoriesResponse;
import com.snapitt.backend_service.modules.story.dto.response.StoryResponse;
import com.snapitt.backend_service.modules.story.model.StoryEntity;
import com.snapitt.backend_service.modules.story.repository.StoryRepository;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * StoryService - Handles story operations with transactional event emission
 *
 * Business Logic:
 * - Create: Save story → Emit STORY_CREATED event (BG job handles story_feed fanout)
 * - Delete: Soft delete story → Emit STORY_DELETED event (BG job handles story_feed cleanup)
 * - No like/unlike functionality (stories are ephemeral)
 *
 * Exception Handling:
 * - AuthException: All business logic exceptions with specific error codes
 * - Generic Exception: Caught and wrapped as INTERNAL_SERVER_ERROR
 */
@Service
@RequiredArgsConstructor
public class StoryService {

    private static final Logger logger = LoggerFactory.getLogger(StoryService.class);

    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;
    private final ProfileService profileService;
    private final EventService eventService;
    private final com.snapitt.backend_service.modules.user.repository.UserRepository userRepository;

    /**
     * Get story by ID with access control
     * @param storyId MongoDB ObjectId
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return StoryResponse with story details
     * @throws AuthException (STORY_NOT_FOUND, 404) - Story not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to this story
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public StoryResponse getStory(String storyId, String viewerId) {
        try {
            StoryEntity story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND));
            
            // Check if story is deleted
            if (story.getIsDeleted() != null && story.getIsDeleted()) {
                throw new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND);
            }
            
            // Check access
            if (!hasAccessToStory(story.getAuthorId(), viewerId)) {
                throw new AuthException("You don't have access to view this story", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }
            
            return mapToResponse(story, viewerId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving story: {}", storyId, ex);
            throw new AuthException("An error occurred while retrieving story", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get paginated stories by username with access control
     * @param username Author's username
     * @param limit Items per page (1-50)
     * @param cursor Opaque cursor for pagination
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return PaginatedStoriesResponse with stories and nextCursor
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to view these stories
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public PaginatedStoriesResponse getStoriesByUsername(String username, int limit, String cursor, String viewerId) {
        try {
            UserEntity user = profileService.getUserByUsername(username);
            if (user == null) {
                throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            // Check access to view this user's stories
            if (!hasAccessToStory(user.getId(), viewerId)) {
                throw new AuthException("You don't have access to view these stories", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            List<StoryEntity> stories;
            if (cursor == null) {
                stories = storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId(), pageRequest);
            } else {
                stories = storyRepository.findByAuthorIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(user.getId(), cursor, pageRequest);
            }

            List<StoryResponse> data = stories.stream()
                    .map(story -> mapToResponse(story, viewerId))
                    .collect(Collectors.toList());

            String nextCursor = stories.isEmpty() ? null : stories.get(stories.size() - 1).getId();

            return PaginatedStoriesResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving stories for username: {}", username, ex);
            throw new AuthException("An error occurred while retrieving stories", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get a story that belongs to the given userId (authenticated user's own story)
     * @param storyId Story ID to fetch
     * @param userId Owner's user ID (from JWT)
     * @return StoryResponse
     */
    public StoryResponse getMyStory(String storyId, String userId) {
        try {
            StoryEntity story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!story.getAuthorId().equals(userId)) {
                throw new AuthException("You don't have access to this story", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            // Check if story is deleted
            if (story.getIsDeleted() != null && story.getIsDeleted()) {
                throw new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            return mapToResponse(story, userId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving my story: {} for user: {}", storyId, userId, ex);
            throw new AuthException("An error occurred while retrieving story", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get paginated stories for the authenticated user
     * @param userId Owner's user ID (from JWT)
     * @param limit page size
     * @param cursor opaque cursor
     * @return PaginatedStoriesResponse
     */
    public PaginatedStoriesResponse getMyStories(String userId, int limit, String cursor) {
        try {
            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            List<StoryEntity> stories;
            if (cursor == null) {
                stories = storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageRequest);
            } else {
                stories = storyRepository.findByAuthorIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(userId, cursor, pageRequest);
            }

            List<StoryResponse> data = stories.stream()
                    .map(story -> mapToResponse(story, userId))
                    .collect(Collectors.toList());

            String nextCursor = stories.isEmpty() ? null : stories.get(stories.size() - 1).getId();

            return PaginatedStoriesResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (Exception ex) {
            logger.error("Error retrieving my stories for user: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving stories", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Create a new story
     * Transactional: Save story → Emit STORY_CREATED event
     * Background job will handle story_feed fanout
     *
     * @param authorId User ID of story author
     * @param createRequest Story creation request
     * @return StoryResponse with created story
     * @throws AuthException (VALIDATION_ERROR, 400) - mediaUrl blank or expiresAt in the past
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public StoryResponse createStory(String authorId, CreateStoryRequest createRequest) {
        try {
            // Validate mediaUrl (required, not blank)
            if (createRequest.getMediaUrl() == null || createRequest.getMediaUrl().isBlank()) {
                throw new AuthException("Media URL is required", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            // Validate expiresAt (must be in the future)
            if (createRequest.getExpiresAt() == null || createRequest.getExpiresAt().isBefore(Instant.now())) {
                throw new AuthException("Expiration time must be in the future", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            // Create story entity
            Instant now = Instant.now();
            StoryEntity story = StoryEntity.builder()
                    .authorId(authorId)
                    .mediaUrl(createRequest.getMediaUrl())
                    .createdAt(now)
                    .expiresAt(createRequest.getExpiresAt())
                    .isDeleted(false)
                    .build();

            StoryEntity saved = storyRepository.save(story);

            // Emit STORY_CREATED event for background job to handle story_feed fanout
            eventService.emitEvent(EventType.STORY_CREATED, saved.getId(), Map.of(
                "storyId", saved.getId(),
                "authorId", authorId,
                "mediaUrl", saved.getMediaUrl(),
                "createdAt", saved.getCreatedAt().toString(),
                "expiresAt", saved.getExpiresAt().toString()
            ));

            return mapToResponse(saved, authorId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error creating story for author: {}", authorId, ex);
            throw new AuthException("An error occurred while creating story", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete a story (soft delete)
     * Transactional: Soft delete story → Emit STORY_DELETED event
     * Background job will handle story_feed cleanup
     *
     * @param storyId Story ID to delete
     * @param authorId User ID requesting deletion (must be author)
     * @throws AuthException (STORY_NOT_FOUND, 404) - Story not found
     * @throws AuthException (FORBIDDEN, 403) - Not story author
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public void deleteStory(String storyId, String authorId) {
        try {
            StoryEntity story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Verify ownership
            if (!story.getAuthorId().equals(authorId)) {
                throw new AuthException("You are not the author of this story", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            // Check if already deleted
            if (story.getIsDeleted() != null && story.getIsDeleted()) {
                throw new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            Instant deletedAt = Instant.now();

            // Soft delete - mark isDeleted = true and set deletedAt timestamp
            story.setIsDeleted(true);
            story.setDeletedAt(deletedAt);
            storyRepository.save(story);

            // Emit STORY_DELETED event with deletedAt timestamp
            // Background job will handle story_feed cleanup
            eventService.emitEvent(EventType.STORY_DELETED, storyId, Map.of(
                "storyId", storyId,
                "authorId", authorId,
                "deletedAt", deletedAt.toString()
            ));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error deleting story: {}", storyId, ex);
            throw new AuthException("An error occurred while deleting story", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check if viewer has access to view stories from a specific author
     * Access is granted if:
     * - Viewer is the story author themselves, OR
     * - Viewer is an approved follower of the story author
     *
     * @param storyAuthorId ID of the story author
     * @param viewerId ID of the viewer (null if not authenticated)
     * @return true if viewer has access, false otherwise
     */
    private boolean hasAccessToStory(String storyAuthorId, String viewerId) {
        // If not authenticated, deny access
        if (viewerId == null) {
            return false;
        }

        // If viewing own stories, allow
        if (viewerId.equals(storyAuthorId)) {
            return true;
        }

        // Check if viewer is an approved follower of the story author
        return followRepository.findByFollowerIdAndFollowingId(viewerId, storyAuthorId)
                .map(follow -> follow.getStatus() == FollowStatus.approved)
                .orElse(false);
    }

    /**
     * Get latest active story from a creator (for feed operations)
     * Queries the latest story that is:
     * - Not deleted (isDeleted = false)
     * - Not expired (expiresAt > now)
     *
     * @param creatorId Creator's user ID
     * @param viewerId Viewer's user ID (for canDelete flag)
     * @return StoryResponse of latest story, or null if no active story
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to creator's stories
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public StoryResponse getLatestStoryByCreator(String creatorId, String viewerId) {
        try {
            Instant now = Instant.now();

            // Query latest story from creator that is not deleted and not expired
            // MongoDB query: { authorId: creatorId, isDeleted: false, expiresAt: { $gt: now } }
            // Sort by createdAt descending to get the latest one
            List<StoryEntity> stories = storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    creatorId,
                    PageRequest.of(0, 1)
            );

            if (stories.isEmpty()) {
                return null;
            }

            StoryEntity latestStory = stories.get(0);

            // Check if story is expired
            if (latestStory.getExpiresAt() != null && latestStory.getExpiresAt().isBefore(now)) {
                return null;  // Story expired
            }

            // Check access (note: feed records only include approved followers)
            // This is a safety check - feed records should already be filtered
            if (!hasAccessToStory(creatorId, viewerId)) {
                throw new AuthException("You don't have access to view this story", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            return mapToResponse(latestStory, viewerId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving latest story for creator: {}", creatorId, ex);
            throw new AuthException("An error occurred while retrieving story", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Map StoryEntity to StoryResponse with viewer context
     * @param story StoryEntity to map
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return StoryResponse with authorUsername and canDelete flag
     */
    private StoryResponse mapToResponse(StoryEntity story, String viewerId) {
        // Fetch author to get username
        String authorUsername = userRepository.findById(story.getAuthorId())
                .map(user -> user.getUsername())
                .orElse("unknown");

        // Determine if viewer can delete (only if viewer is the author)
        Boolean canDelete = viewerId != null && viewerId.equals(story.getAuthorId());

        return StoryResponse.builder()
                .id(story.getId())
                .authorUsername(authorUsername)
                .mediaUrl(story.getMediaUrl())
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .canDelete(canDelete)
                .build();
    }
}
