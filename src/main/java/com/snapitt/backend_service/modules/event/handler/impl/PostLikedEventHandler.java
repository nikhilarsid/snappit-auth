package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationType;
import com.snapitt.backend_service.modules.notification.repository.NotificationRepository;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostLikedEventHandler implements EventHandler {

    private final PostRepository postRepository;
    private final NotificationRepository notificationRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing POST_LIKED event: {}", event.getAggregateId());

        String postId = event.getAggregateId();
        String userId = (String) event.getPayload().get("userId");

        try {
            // Fetch the post
            Optional<PostEntity> postOpt = postRepository.findById(postId);
            if (postOpt.isEmpty()) {
                log.warn("Post {} not found for like event", postId);
                return;
            }

            PostEntity post = postOpt.get();

            // Increment like count
            if (post.getLikeCount() == null) {
                post.setLikeCount(1L);
            } else {
                post.setLikeCount(post.getLikeCount() + 1);
            }

            // Save updated post
            postRepository.save(post);
            log.info("Successfully incremented like count for post {} to {}", postId, post.getLikeCount());

            // Create LIKE notification for post author (skip self-like)
            String authorId = (String) event.getPayload().get("authorId");
            if (authorId != null && !authorId.equals(userId)) {
                NotificationEntity notification = NotificationEntity.builder()
                        .targetUserId(authorId)
                        .actorId(userId)
                        .type(NotificationType.LIKE)
                        .entityId(postId)
                        .seen(false)
                        .createdAt(Instant.now())
                        .build();
                notificationRepository.save(notification);
                log.info("Created LIKE notification for user {} from user {}", authorId, userId);
            }
        } catch (Exception ex) {
            log.error("Error processing POST_LIKED event for post {}", postId, ex);
            throw ex;
        }
    }
}
