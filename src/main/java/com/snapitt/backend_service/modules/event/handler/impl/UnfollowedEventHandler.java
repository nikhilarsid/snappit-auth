package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import com.snapitt.backend_service.modules.feed.repository.StoryFeedRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnfollowedEventHandler implements EventHandler {

    private final UserRepository userRepository;
    private final PostFeedRepository postFeedRepository;
    private final StoryFeedRepository storyFeedRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing UNFOLLOWED event: {}", event.getAggregateId());

        String followRelationshipId = event.getAggregateId();
        String unfollowerId = (String) event.getPayload().get("followerId");    
        String unfollowedId = (String) event.getPayload().get("followingId");   

        try {
            
            Optional<UserEntity> unfollowerOpt = userRepository.findById(unfollowerId);
            if (unfollowerOpt.isPresent()) {
                UserEntity unfollower = unfollowerOpt.get();
                if (unfollower.getFollowingCount() != null && unfollower.getFollowingCount() > 0) {
                    unfollower.setFollowingCount(unfollower.getFollowingCount() - 1);
                } else {
                    unfollower.setFollowingCount(0L);
                }
                userRepository.save(unfollower);
                log.info("Decremented following count for user {} to {}", unfollowerId, unfollower.getFollowingCount());
            } else {
                log.warn("Unfollower user {} not found", unfollowerId);
            }

            Optional<UserEntity> unfollowedOpt = userRepository.findById(unfollowedId);
            if (unfollowedOpt.isPresent()) {
                UserEntity unfollowed = unfollowedOpt.get();
                if (unfollowed.getFollowersCount() != null && unfollowed.getFollowersCount() > 0) {
                    unfollowed.setFollowersCount(unfollowed.getFollowersCount() - 1);
                } else {
                    unfollowed.setFollowersCount(0L);
                }
                userRepository.save(unfollowed);
                log.info("Decremented followers count for user {} to {}", unfollowedId, unfollowed.getFollowersCount());
            } else {
                log.warn("Unfollowed user {} not found", unfollowedId);
            }

            long deletedPostCount = postFeedRepository.deleteByUserIdAndAuthorId(unfollowerId, unfollowedId);
            log.info("Removed {} posts from user {} to {}'s feed", deletedPostCount, unfollowedId, unfollowerId);

            long deletedStoryCount = storyFeedRepository.deleteByUserIdAndCreatorId(unfollowerId, unfollowedId);
            log.info("Removed {} stories from user {} to {}'s feed", deletedStoryCount, unfollowedId, unfollowerId);

            log.info("Successfully processed unfollow from {} to {}", unfollowerId, unfollowedId);
        } catch (Exception ex) {
            log.error("Error processing UNFOLLOWED event for follow relationship {}", followRelationshipId, ex);
            throw ex;
        }
    }
}
