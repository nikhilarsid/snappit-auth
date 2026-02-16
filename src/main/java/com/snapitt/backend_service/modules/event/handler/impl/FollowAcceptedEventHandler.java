package com.snapitt.backend_service.modules.event.handler.impl;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles FOLLOW_ACCEPTED event.
 * 
 * When a follow request is accepted:
 * 1. Increment following count for requester
 * 2. Increment followers count for recipient
 * 3. Insert notification for the requester
 * 4. Populate post_feed with recent posts from the newly followed account
 * 5. Mark event as done
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowAcceptedEventHandler implements EventHandler {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostFeedRepository postFeedRepository;

    @Override
    public void handle(EventEntity event) throws Exception {
        log.info("Processing FOLLOW_ACCEPTED event: {}", event.getAggregateId());

        String followRequestId = event.getAggregateId();
        String followerId = (String) event.getPayload().get("followerId");
        String followingId = (String) event.getPayload().get("followingId");

        try {
            // Increment follower's followingCount
            Optional<UserEntity> followerOpt = userRepository.findById(followerId);
            if (followerOpt.isPresent()) {
                UserEntity follower = followerOpt.get();
                if (follower.getFollowingCount() == null) {
                    follower.setFollowingCount(1L);
                } else {
                    follower.setFollowingCount(follower.getFollowingCount() + 1);
                }
                userRepository.save(follower);
                log.info("Incremented following count for user {} to {}", followerId, follower.getFollowingCount());
            } else {
                log.warn("Follower user {} not found", followerId);
            }

            // Increment following user's followersCount
            Optional<UserEntity> followingOpt = userRepository.findById(followingId);
            if (followingOpt.isPresent()) {
                UserEntity following = followingOpt.get();
                if (following.getFollowersCount() == null) {
                    following.setFollowersCount(1L);
                } else {
                    following.setFollowersCount(following.getFollowersCount() + 1);
                }
                userRepository.save(following);
                log.info("Incremented followers count for user {} to {}", followingId, following.getFollowersCount());
            } else {
                log.warn("Following user {} not found", followingId);
            }

            // Fetch recent posts from the followed user and add to follower's post_feed
            List<PostEntity> recentPosts = postRepository.findByAuthorIdOrderByCreatedAtDesc(
                    followingId,
                    PageRequest.of(0, 10)  // Fetch 10 most recent posts
            );

            log.debug("Found {} recent posts from user {}", recentPosts.size(), followingId);

            // Add recent posts to follower's post_feed
            List<PostFeedEntity> feedEntries = new ArrayList<>();
            for (PostEntity post : recentPosts) {
                PostFeedEntity feedEntry = PostFeedEntity.builder()
                        .userId(followerId)
                        .postId(post.getId())
                        .authorId(followingId)
                        .createdAt(post.getCreatedAt())
                        .seen(false)
                        .insertedAt(Instant.now())
                        .build();
                feedEntries.add(feedEntry);
            }

            if (!feedEntries.isEmpty()) {
                postFeedRepository.saveAll(feedEntries);
                log.info("Added {} recent posts from {} to {}'s feed", feedEntries.size(), followingId, followerId);
            }

            // TODO: Insert follow notification for the follower
            // - Create notification document with type='follow_accepted', follower_id, following_id
            // - Insert into notifications collection

            log.info("Successfully accepted follow request from {} to {}", followerId, followingId);
        } catch (Exception ex) {
            log.error("Error processing FOLLOW_ACCEPTED event for follow request {}", followRequestId, ex);
            throw ex;
        }
    }
}
