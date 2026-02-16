package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.feed.model.StoryFeedEntity;
import com.snapitt.backend_service.modules.feed.repository.StoryFeedRepository;
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
 * Handles STORY_CREATED event.
 * 
 * When a story is created:
 * 1. Fetch all approved followers of the story author
 * 2. Upsert story_feed entries for each follower
 * 3. Mark event as done
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoryCreatedEventHandler implements EventHandler {

    private final FollowRepository followRepository;
    private final StoryFeedRepository storyFeedRepository;

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

            // Prepare story_feed entries for all followers
            List<StoryFeedEntity> feedEntries = new ArrayList<>();
            for (var follower : followers) {
                StoryFeedEntity feedEntry = StoryFeedEntity.builder()
                        .userId(follower.getFollowerId())
                        .creatorId(authorId)
                        .latestStoryAt(createdAt)
                        .seen(false)
                        .isDeleted(false)
                        .build();
                feedEntries.add(feedEntry);
            }

            // Bulk insert into story_feed collection (upsert by userId + creatorId unique constraint)
            if (!feedEntries.isEmpty()) {
                storyFeedRepository.saveAll(feedEntries);
                log.info("Distributed story {} to {} followers", storyId, feedEntries.size());
            } else {
                log.info("Story {} has no followers, skipping feed distribution", storyId);
            }
        } catch (Exception ex) {
            log.error("Error processing STORY_CREATED event for story {}", storyId, ex);
            throw ex;
        }
    }
}
