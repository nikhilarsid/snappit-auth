package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles POST_CREATED event.
 * 
 * When a post is created:
 * 1. Fetch all approved followers of the post author
 * 2. Bulk insert entries into post_feed for each follower
 * 3. Mark event as done
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCreatedEventHandler implements EventHandler {

    private final FollowRepository followRepository;
    private final PostFeedRepository postFeedRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing POST_CREATED event: {}", event.getAggregateId());

        String postId = event.getAggregateId();
        String authorId = (String) event.getPayload().get("authorId");
        Instant createdAt = Instant.now();

        try {
            // Fetch all approved followers of the post author
            var followers = followRepository.findByFollowingIdAndStatusOrderByIdDesc(
                    authorId,
                    FollowStatus.approved,
                    PageRequest.of(0, 10000)  // Large page size to get all followers
            );

            log.debug("Found {} approved followers for author {}", followers.size(), authorId);

            // Prepare post_feed entries for all followers
            List<PostFeedEntity> feedEntries = new ArrayList<>();
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

            // Bulk insert into post_feed collection
            if (!feedEntries.isEmpty()) {
                postFeedRepository.saveAll(feedEntries);
                log.info("Distributed post {} to {} followers", postId, feedEntries.size());
            } else {
                log.info("Post {} has no followers, skipping feed distribution", postId);
            }
        } catch (Exception ex) {
            log.error("Error processing POST_CREATED event for post {}", postId, ex);
            throw ex;
        }
    }
}
