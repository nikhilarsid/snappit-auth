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
public class PostUnlikedEventHandler implements EventHandler {

    private final PostRepository postRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing POST_UNLIKED event: {}", event.getAggregateId());

        String postId = (String) event.getPayload().get("postId");
        String userId = (String) event.getPayload().get("userId");

        try {
            Optional<PostEntity> postOpt = postRepository.findById(postId);
            if (postOpt.isPresent()) {
                PostEntity post = postOpt.get();
                if (post.getLikeCount() != null && post.getLikeCount() > 0) {
                    post.setLikeCount(post.getLikeCount() - 1);
                } else {
                    post.setLikeCount(0L);
                }
                postRepository.save(post);
                log.info("Decremented like count for post {} by user {}, new count: {}", postId, userId, post.getLikeCount());
            } else {
                log.warn("Post {} not found for unlike operation", postId);
            }

            log.info("Successfully processed unlike on post {}", postId);
        } catch (Exception ex) {
            log.error("Error processing POST_UNLIKED event for post {}", postId, ex);
            throw ex;
        }
    }
}
