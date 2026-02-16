package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentDeletedEventHandler implements EventHandler {

    private final PostRepository postRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing COMMENT_DELETED event: {}", event.getAggregateId());

        String postId = (String) event.getPayload().get("postId");
        String commentId = (String) event.getPayload().get("commentId");

        try {
            Optional<PostEntity> postOpt = postRepository.findById(postId);
            if (postOpt.isEmpty()) {
                log.warn("Post {} not found for COMMENT_DELETED event", postId);
                return;
            }

            PostEntity post = postOpt.get();
            long current = post.getCommentCount() != null ? post.getCommentCount() : 0;
            post.setCommentCount(Math.max(0, current - 1));
            postRepository.save(post);

            log.info("Decremented comment count for post {} to {} (deleted comment: {})",
                    postId, post.getCommentCount(), commentId);
        } catch (Exception ex) {
            log.error("Error processing COMMENT_DELETED event for comment {}", commentId, ex);
            throw ex;
        }
    }
}
