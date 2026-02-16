package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoryCreatedEventHandler implements EventHandler {

    private final FollowRepository followRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing STORY_CREATED event: {}", event.getAggregateId());

        String storyId = event.getAggregateId();
        String authorId = (String) event.getPayload().get("authorId");
        Instant createdAt = Instant.now();

        try {
            // Fetch all approved followers of the story author
            var followers = followRepository.findByFollowingIdAndStatusOrderByIdDesc(
                    authorId,
                    FollowStatus.approved,
                    PageRequest.of(0, 10000)  // Large page size to get all followers
            );

            log.debug("Found {} approved followers for author {}", followers.size(), authorId);

            // Upsert story_feed entries for all followers (handles duplicate key gracefully)
            int upsertCount = 0;
            for (var follower : followers) {
                mongoTemplate.upsert(
                        Query.query(Criteria.where("userId").is(follower.getFollowerId())
                                .and("creatorId").is(authorId)),
                        new Update()
                                .set("latestStoryAt", createdAt)
                                .set("seen", false)
                                .set("isDeleted", false),
                        "story_feed"
                );
                upsertCount++;
            }

            if (upsertCount > 0) {
                log.info("Distributed story {} to {} followers (upsert)", storyId, upsertCount);
            } else {
                log.info("Story {} has no followers, skipping feed distribution", storyId);
            }
        } catch (Exception ex) {
            log.error("Error processing STORY_CREATED event for story {}", storyId, ex);
            throw ex;
        }
    }
}
