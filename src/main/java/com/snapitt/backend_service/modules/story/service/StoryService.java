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

@Service
@RequiredArgsConstructor
public class StoryService {

    private static final Logger logger = LoggerFactory.getLogger(StoryService.class);

    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;
    private final ProfileService profileService;
    private final EventService eventService;
    private final com.snapitt.backend_service.modules.user.repository.UserRepository userRepository;

    public StoryResponse getStory(String storyId, String viewerId) {
        try {
            StoryEntity story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND));
            
            if (story.getIsDeleted() != null && story.getIsDeleted()) {
                throw new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND);
            }
            
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

    public PaginatedStoriesResponse getStoriesByUsername(String username, int limit, String cursor, String viewerId) {
        try {
            UserEntity user = profileService.getUserByUsername(username);
            if (user == null) {
                throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

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

    public StoryResponse getMyStory(String storyId, String userId) {
        try {
            StoryEntity story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!story.getAuthorId().equals(userId)) {
                throw new AuthException("You don't have access to this story", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

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

    @Transactional
    public StoryResponse createStory(String authorId, CreateStoryRequest createRequest) {
        try {
            
            if (createRequest.getMediaUrl() == null || createRequest.getMediaUrl().isBlank()) {
                throw new AuthException("Media URL is required", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            if (createRequest.getExpiresAt() == null || createRequest.getExpiresAt().isBefore(Instant.now())) {
                throw new AuthException("Expiration time must be in the future", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            Instant now = Instant.now();
            StoryEntity story = StoryEntity.builder()
                    .authorId(authorId)
                    .mediaUrl(createRequest.getMediaUrl())
                    .createdAt(now)
                    .expiresAt(createRequest.getExpiresAt())
                    .isDeleted(false)
                    .build();

            StoryEntity saved = storyRepository.save(story);

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

    @Transactional
    public void deleteStory(String storyId, String authorId) {
        try {
            StoryEntity story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!story.getAuthorId().equals(authorId)) {
                throw new AuthException("You are not the author of this story", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            if (story.getIsDeleted() != null && story.getIsDeleted()) {
                throw new AuthException("Story not found", "STORY_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            Instant deletedAt = Instant.now();

            story.setIsDeleted(true);
            story.setDeletedAt(deletedAt);
            storyRepository.save(story);

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

    private boolean hasAccessToStory(String storyAuthorId, String viewerId) {
        
        if (viewerId == null) {
            return false;
        }

        if (viewerId.equals(storyAuthorId)) {
            return true;
        }

        return followRepository.findByFollowerIdAndFollowingId(viewerId, storyAuthorId)
                .map(follow -> follow.getStatus() == FollowStatus.approved)
                .orElse(false);
    }

    public StoryResponse getLatestStoryByCreator(String creatorId, String viewerId) {
        try {
            Instant now = Instant.now();

            List<StoryEntity> stories = storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    creatorId,
                    PageRequest.of(0, 1)
            );

            if (stories.isEmpty()) {
                return null;
            }

            StoryEntity latestStory = stories.get(0);

            if (latestStory.getExpiresAt() != null && latestStory.getExpiresAt().isBefore(now)) {
                return null;  
            }

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

    private StoryResponse mapToResponse(StoryEntity story, String viewerId) {
        
        var authorUser = userRepository.findById(story.getAuthorId()).orElse(null);
        String authorUsername = authorUser != null ? authorUser.getUsername() : "unknown";
        String authorAvatarUrl = (authorUser != null && authorUser.getProfile() != null)
                ? authorUser.getProfile().getAvatarUrl() : null;

        Boolean canDelete = viewerId != null && viewerId.equals(story.getAuthorId());

        return StoryResponse.builder()
                .id(story.getId())
                .authorUsername(authorUsername)
                .authorAvatarUrl(authorAvatarUrl)
                .mediaUrl(story.getMediaUrl())
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .canDelete(canDelete)
                .build();
    }
}
