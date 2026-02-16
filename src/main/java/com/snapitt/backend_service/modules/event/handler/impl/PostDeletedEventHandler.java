package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Handles POST_DELETED event.
 * 
 * When a post is deleted:
 * 1. Remove the post from all users' post_feed collections
 * 2. Mark event as done
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostDeletedEventHandler implements EventHandler {

    private final PostFeedRepository postFeedRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing POST_DELETED event: {}", event.getAggregateId());

        String postId = (String) event.getPayload().get("postId");
        String authorId = (String) event.getPayload().get("authorId");

        try {
            // Decrement post count on the user
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(authorId)),
                    new Update().inc("postCount", -1),
                    "users"
            );
            // Remove this post from all users' post_feed
            long deletedCount = postFeedRepository.deleteByPostId(postId);
            log.info("Removed deleted post {} from {} users' feeds", postId, deletedCount);

            log.info("Successfully processed deletion of post {} by author {}", postId, authorId);
        } catch (Exception ex) {
            log.error("Error processing POST_DELETED event for post {}", postId, ex);
            throw ex;
        }
    }
}
