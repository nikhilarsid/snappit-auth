package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.comment.model.CommentEntity;
import com.snapitt.backend_service.modules.comment.repository.CommentRepository;
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
public class CommentCreatedEventHandler implements EventHandler {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final NotificationRepository notificationRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing COMMENT_CREATED event: {}", event.getAggregateId());

        String commentId = (String) event.getPayload().get("commentId");
        String postId = (String) event.getPayload().get("postId");
        String parentCommentId = (String) event.getPayload().get("parentCommentId");
        String userId = (String) event.getPayload().get("authorId");

        try {
            
            Optional<PostEntity> postOpt = postRepository.findById(postId);
            if (postOpt.isEmpty()) {
                log.warn("Post {} not found for comment event", postId);
                return;
            }

            PostEntity post = postOpt.get();
            if (post.getCommentCount() == null) {
                post.setCommentCount(1L);
            } else {
                post.setCommentCount(post.getCommentCount() + 1);
            }
            postRepository.save(post);
            log.info("Incremented comment count for post {} to {}", postId, post.getCommentCount());

            if (parentCommentId != null && !parentCommentId.isBlank()) {
                Optional<CommentEntity> parentOpt = commentRepository.findById(parentCommentId);
                if (parentOpt.isPresent()) {
                    CommentEntity parentComment = parentOpt.get();
                    if (parentComment.getReplyCount() == null) {
                        parentComment.setReplyCount(1L);
                    } else {
                        parentComment.setReplyCount(parentComment.getReplyCount() + 1);
                    }
                    commentRepository.save(parentComment);
                    log.info("Incremented reply count for parent comment {}", parentCommentId);
                } else {
                    log.warn("Parent comment {} not found", parentCommentId);
                }
            }

            if (!userId.equals(post.getAuthorId())) {
                NotificationEntity notification = NotificationEntity.builder()
                        .targetUserId(post.getAuthorId())
                        .actorId(userId)
                        .type(NotificationType.COMMENT)
                        .entityId(postId)
                        .seen(false)
                        .createdAt(Instant.now())
                        .build();
                notificationRepository.save(notification);
                log.info("Created COMMENT notification for user {} from user {}", post.getAuthorId(), userId);
            }

            log.info("Successfully recorded comment {} on post {}", commentId, postId);
        } catch (Exception ex) {
            log.error("Error processing COMMENT_CREATED event for comment {}", commentId, ex);
            throw ex;
        }
    }
}
