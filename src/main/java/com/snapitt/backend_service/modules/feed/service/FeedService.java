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

@Service
@RequiredArgsConstructor
public class FeedService {

    private static final Logger logger = LoggerFactory.getLogger(FeedService.class);

    private final PostFeedRepository postFeedRepository;
    private final StoryFeedRepository storyFeedRepository;
    private final PostService postService;
    private final StoryService storyService;
    private final ProfileService profileService;

    public PaginatedFeedPostsResponse getMyFeedPosts(String userId, int limit, String cursor) {
        try {
            
            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

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

            List<String> postIds = feedRecords.stream()
                    .map(PostFeedEntity::getPostId)
                    .collect(Collectors.toList());

            List<PostResponse> posts = postService.getPostsByIds(postIds, userId);

            Map<String, PostResponse> postMap = posts.stream()
                    .collect(Collectors.toMap(PostResponse::getId, p -> p));

            List<FeedPostDto> feedItems = feedRecords.stream()
                    .map(record -> {
                        PostResponse post = postMap.get(record.getPostId());
                        
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
                    .filter(item -> item != null)  
                    .collect(Collectors.toList());

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

    public PaginatedFeedStoriesResponse getMyFeedStories(String userId, int limit, String cursor) {
        try {
            
            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

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

            List<String> creatorIds = feedRecords.stream()
                    .map(StoryFeedEntity::getCreatorId)
                    .collect(Collectors.toList());

            List<UserEntity> creators = profileService.getUsersByIds(creatorIds);

            Map<String, UserEntity> creatorMap = creators.stream()
                    .collect(Collectors.toMap(UserEntity::getId, c -> c));

            List<FeedStoryDto> feedItems = feedRecords.stream()
                    .map(record -> {
                        UserEntity creator = creatorMap.get(record.getCreatorId());
                        
                        if (creator == null) {
                            logger.warn("Creator not found for story_feed record: {}", record.getId());
                            return null;
                        }

                        StoryResponse latestStory = storyService.getLatestStoryByCreator(record.getCreatorId(), userId);

                        if (latestStory == null) {
                            logger.debug("No active story found for creator: {} in feed", record.getCreatorId());
                            return null;
                        }

                        String avatarUrl = creator.getProfile() != null ? creator.getProfile().getAvatarUrl() : null;

                        return FeedStoryDto.builder()
                                .feedStoryId(record.getId())
                                .creatorUsername(creator.getUsername())
                                .creatorAvatarUrl(avatarUrl)
                                .storyId(latestStory.getId())
                                .seen(record.getSeen())
                                .latestStoryAt(record.getLatestStoryAt())
                                .build();
                    })
                    .filter(item -> item != null)  
                    .collect(Collectors.toList());

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

    public void markPostAsRead(String postFeedId, String userId) {
        try {
            PostFeedEntity feedRecord = postFeedRepository.findById(postFeedId)
                    .orElseThrow(() -> new AuthException("Feed record not found", "POST_FEED_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!feedRecord.getUserId().equals(userId)) {
                throw new AuthException("This feed record doesn't belong to you", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

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

    public void markStoryAsRead(String storyFeedId, String userId) {
        try {
            StoryFeedEntity feedRecord = storyFeedRepository.findById(storyFeedId)
                    .orElseThrow(() -> new AuthException("Feed record not found", "STORY_FEED_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!feedRecord.getUserId().equals(userId)) {
                throw new AuthException("This feed record doesn't belong to you", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

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

    public void markStoryAsReadByCreator(String viewerId, String creatorUsername) {
        try {
            var creatorUser = profileService.getUserByUsername(creatorUsername);
            if (creatorUser == null) {
                logger.debug("Creator user not found: {}", creatorUsername);
                return; 
            }

            var feedOpt = storyFeedRepository.findByUserIdAndCreatorIdAndIsDeletedFalse(viewerId, creatorUser.getId());
            feedOpt.ifPresent(feed -> {
                if (!Boolean.TRUE.equals(feed.getSeen())) {
                    feed.setSeen(true);
                    storyFeedRepository.save(feed);
                    logger.debug("Marked story from {} as seen for user {}", creatorUsername, viewerId);
                }
            });
        } catch (Exception ex) {
            logger.error("Error marking story as read by creator: {} for user: {}", creatorUsername, viewerId, ex);
            
        }
    }
}
