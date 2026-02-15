package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Optional;

/**
 * Handles POST_LIKED event.
 * 
 * When a post is liked:
 * 1. Increment posts.likeCount
 * 2. Insert a like notification for the post author
 * 3. Mark event as done
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostLikedEventHandler implements EventHandler {

    private final PostRepository postRepository;

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

            // TODO: Insert like notification for the post author
            // - Create notification document with type='like', liker_user_id, post_id, post_author_id
            // - Insert into notifications collection
        } catch (Exception ex) {
            log.error("Error processing POST_LIKED event for post {}", postId, ex);
            throw ex;
        }
    }
}
