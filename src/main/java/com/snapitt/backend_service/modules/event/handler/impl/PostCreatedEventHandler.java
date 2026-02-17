package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostCreatedEventHandler implements EventHandler {

    private final FollowRepository followRepository;
    private final PostFeedRepository postFeedRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing POST_CREATED event: {}", event.getAggregateId());

        String postId = event.getAggregateId();
        String authorId = (String) event.getPayload().get("authorId");
        Instant createdAt = Instant.now();

        try {
            
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(authorId)),
                    new Update().inc("postCount", 1),
                    "users"
            );

            var followers = followRepository.findByFollowingIdAndStatusOrderByIdDesc(
                    authorId,
                    FollowStatus.approved,
                    PageRequest.of(0, 10000)  
            );

            log.debug("Found {} approved followers for author {}", followers.size(), authorId);

            List<PostFeedEntity> feedEntries = new ArrayList<>();

            // Add to author's own feed
            feedEntries.add(PostFeedEntity.builder()
                    .userId(authorId)
                    .postId(postId)
                    .authorId(authorId)
                    .createdAt(createdAt)
                    .seen(true)
                    .insertedAt(Instant.now())
                    .build());

            for (var follower : followers) {
                PostFeedEntity feedEntry = PostFeedEntity.builder()
                        .userId(follower.getFollowerId())
                        .postId(postId)
                        .authorId(authorId)
                        .createdAt(createdAt)
                        .seen(false)
                        .insertedAt(Instant.now())
                        .build();
                feedEntries.add(feedEntry);
            }

            postFeedRepository.saveAll(feedEntries);
            log.info("Distributed post {} to author + {} followers", postId, followers.size());
        } catch (Exception ex) {
            log.error("Error processing POST_CREATED event for post {}", postId, ex);
            throw ex;
        }
    }
}
