package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import com.snapitt.backend_service.modules.notification.model.NotificationEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationType;
import com.snapitt.backend_service.modules.notification.repository.NotificationRepository;
import com.snapitt.backend_service.modules.notification.websocket.NotificationWebSocketHandler;
import com.snapitt.backend_service.modules.notification.websocket.WebSocketNotificationPayload;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import com.snapitt.backend_service.modules.story.repository.StoryRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowAcceptedEventHandler implements EventHandler {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostFeedRepository postFeedRepository;
    private final NotificationRepository notificationRepository;
    private final StoryRepository storyRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationWebSocketHandler webSocketHandler;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing FOLLOW_ACCEPTED event: {}", event.getAggregateId());

        String followRequestId = event.getAggregateId();
        String followerId = (String) event.getPayload().get("followerId");
        String followingId = (String) event.getPayload().get("followingId");

        try {
            
            Optional<UserEntity> followerOpt = userRepository.findById(followerId);
            if (followerOpt.isPresent()) {
                UserEntity follower = followerOpt.get();
                if (follower.getFollowingCount() == null) {
                    follower.setFollowingCount(1L);
                } else {
                    follower.setFollowingCount(follower.getFollowingCount() + 1);
                }
                userRepository.save(follower);
                log.info("Incremented following count for user {} to {}", followerId, follower.getFollowingCount());
            } else {
                log.warn("Follower user {} not found", followerId);
            }

            Optional<UserEntity> followingOpt = userRepository.findById(followingId);
            if (followingOpt.isPresent()) {
                UserEntity following = followingOpt.get();
                if (following.getFollowersCount() == null) {
                    following.setFollowersCount(1L);
                } else {
                    following.setFollowersCount(following.getFollowersCount() + 1);
                }
                userRepository.save(following);
                log.info("Incremented followers count for user {} to {}", followingId, following.getFollowersCount());
            } else {
                log.warn("Following user {} not found", followingId);
            }

            // Only fan out the most recent 3 posts that are no older than 3 days
            Instant threeDaysAgo = Instant.now().minus(Duration.ofDays(3));
            List<PostEntity> recentPosts = postRepository.findByAuthorIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    followingId,
                    threeDaysAgo,
                    PageRequest.of(0, 3)
            );

            log.debug("Found {} recent posts (within 3 days) from user {}", recentPosts.size(), followingId);

            List<PostFeedEntity> feedEntries = new ArrayList<>();
            for (PostEntity post : recentPosts) {
                PostFeedEntity feedEntry = PostFeedEntity.builder()
                        .userId(followerId)
                        .postId(post.getId())
                        .authorId(followingId)
                        .createdAt(post.getCreatedAt())
                        .seen(false)
                        .insertedAt(Instant.now())
                        .build();
                feedEntries.add(feedEntry);
            }

            if (!feedEntries.isEmpty()) {
                postFeedRepository.saveAll(feedEntries);
                log.info("Added {} recent posts from {} to {}'s feed", feedEntries.size(), followingId, followerId);
            }

            NotificationEntity notification = NotificationEntity.builder()
                    .targetUserId(followerId)
                    .actorId(followingId)
                    .type(NotificationType.FOLLOW_ACCEPTED)
                    .entityId(followingId)
                    .seen(false)
                    .createdAt(Instant.now())
                    .build();
            notificationRepository.save(notification);

            webSocketHandler.sendToUser(followerId, WebSocketNotificationPayload.builder()
                    .action("NEW")
                    .notificationId(notification.getId())
                    .type(NotificationType.FOLLOW_ACCEPTED)
                    .actorId(followingId)
                    .entityId(followingId)
                    .createdAt(notification.getCreatedAt())
                    .build());

            log.info("Created FOLLOW_ACCEPTED notification for user {} from user {}", followerId, followingId);

            boolean hasActiveStories = storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(
                    followingId, Instant.now());
            if (hasActiveStories) {
                mongoTemplate.upsert(
                        Query.query(Criteria.where("userId").is(followerId).and("creatorId").is(followingId)),
                        new Update()
                                .set("latestStoryAt", Instant.now())
                                .set("seen", false)
                                .set("isDeleted", false),
                        "story_feed"
                );
                log.info("Added story feed entry for follower {} from creator {}", followerId, followingId);
            }

            log.info("Successfully accepted follow request from {} to {}", followerId, followingId);
        } catch (Exception ex) {
            log.error("Error processing FOLLOW_ACCEPTED event for follow request {}", followRequestId, ex);
            throw ex;
        }
    }
}
