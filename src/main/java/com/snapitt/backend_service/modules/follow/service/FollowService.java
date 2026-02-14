package com.snapitt.backend_service.modules.follow.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final ProfileService profileService;
    private final EventService eventService;

    @Transactional
    public void createFollowRequest(String followerId, String targetUsername) {
        if (followerId == null || followerId.isBlank()) {
            throw new com.snapitt.backend_service.modules.auth.common.exception.AuthException("Unauthorized", "UNAUTHORIZED", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        UserEntity targetUser = profileService.getUserByUsername(targetUsername);
        if (targetUser == null) {
            throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        if (targetUser.getId() != null && targetUser.getId().equals(followerId)) {
            throw new AuthException("Cannot follow self", "CANNOT_FOLLOW_SELF", HttpStatus.BAD_REQUEST);
        }

        var existing = followRepository.findByFollowerIdAndFollowingId(followerId, targetUser.getId()).orElse(null);
        if (existing != null && existing.getStatus() == FollowStatus.approved) {
            throw new AuthException("Already following", "ALREADY_FOLLOWING", HttpStatus.CONFLICT);
        }
        if (existing != null && existing.getStatus() == FollowStatus.pending) {
            throw new AuthException("Request already sent", "REQUEST_ALREADY_SENT", HttpStatus.CONFLICT);
        }

        FollowEntity saved;
        if (existing != null && existing.getStatus() == FollowStatus.rejected) {
            existing.setStatus(FollowStatus.pending);
            existing.setCreatedAt(Instant.now());
            saved = followRepository.save(existing);
        } else {
            FollowEntity follow = FollowEntity.builder()
                    .followerId(followerId)
                    .followingId(targetUser.getId())
                    .status(FollowStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            saved = followRepository.save(follow);
        }

        // Emit FOLLOW_REQUESTED event (transactional - allow rollback on failure)
        eventService.emitEvent(EventType.FOLLOW_REQUESTED, targetUser.getId(), Map.of(
            "followerId", followerId,
            "followingId", targetUser.getId(),
            "relationId", saved.getId()
        ));
    }

    @Transactional
    public void approveFollow(String currentUserId, String followerUsername) {
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new com.snapitt.backend_service.modules.auth.common.exception.AuthException("Unauthorized", "UNAUTHORIZED", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        UserEntity follower = profileService.getUserByUsername(followerUsername);
        if (follower == null) {
            throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        var relationOpt = followRepository.findByFollowerIdAndFollowingId(follower.getId(), currentUserId);
        var relation = relationOpt.orElse(null);
        if (relation == null || relation.getStatus() != FollowStatus.pending) {
            throw new AuthException("No pending request", "NO_PENDING_REQUEST", HttpStatus.NOT_FOUND);
        }
        relation.setStatus(FollowStatus.approved);
        relation.setCreatedAt(Instant.now());
        var saved = followRepository.save(relation);

        eventService.emitEvent(EventType.FOLLOW_ACCEPTED, saved.getId(), Map.of(
            "followerId", follower.getId(),
            "followingId", currentUserId,
            "relationId", saved.getId()
        ));
    }

    @Transactional
    public void rejectFollow(String currentUserId, String followerUsername) {
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new com.snapitt.backend_service.modules.auth.common.exception.AuthException("Unauthorized", "UNAUTHORIZED", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        UserEntity follower = profileService.getUserByUsername(followerUsername);
        if (follower == null) {
            throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        var relationOpt = followRepository.findByFollowerIdAndFollowingId(follower.getId(), currentUserId);
        var relation = relationOpt.orElse(null);
        if (relation == null || relation.getStatus() != FollowStatus.pending) {
            throw new AuthException("No pending request", "NO_PENDING_REQUEST", HttpStatus.NOT_FOUND);
        }
        relation.setStatus(FollowStatus.rejected);
        relation.setCreatedAt(Instant.now());
        var saved = followRepository.save(relation);

        eventService.emitEvent(EventType.FOLLOW_REJECTED, saved.getId(), Map.of(
            "followerId", follower.getId(),
            "followingId", currentUserId,
            "relationId", saved.getId()
        ));
    }

    @Transactional
    public void unfollow(String followerId, String targetUsername) {
        if (followerId == null || followerId.isBlank()) {
            throw new com.snapitt.backend_service.modules.auth.common.exception.AuthException("Unauthorized", "UNAUTHORIZED", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        UserEntity targetUser = profileService.getUserByUsername(targetUsername);
        if (targetUser == null) {
            throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        if (targetUser.getId() != null && targetUser.getId().equals(followerId)) {
            throw new AuthException("Cannot unfollow self", "CANNOT_UNFOLLOW_SELF", HttpStatus.BAD_REQUEST);
        }

        var relationOpt = followRepository.findByFollowerIdAndFollowingId(followerId, targetUser.getId());
        var relation = relationOpt.orElse(null);
        if (relation == null || relation.getStatus() != FollowStatus.approved) {
            throw new AuthException("Not following", "NOT_FOLLOWING", HttpStatus.NOT_FOUND);
        }

        // relation is guaranteed non-null here, reuse it for event payload
        String relationId = relation.getId();

        followRepository.deleteByFollowerIdAndFollowingId(followerId, targetUser.getId());

        eventService.emitEvent(EventType.UNFOLLOWED, targetUser.getId(), Map.of(
            "followerId", followerId,
            "followingId", targetUser.getId(),
            "relationId", relationId
        ));
    }
    public java.util.Map<String, Object> getFollowers(String username, int limit, String cursor, String viewerId) {
        UserEntity user = profileService.getUserByUsername(username);
        if (user == null) {
            throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        int pageSize = Math.max(1, Math.min(limit, 50));//edge case limit>0-->puts pagesize=1
        PageRequest pr = PageRequest.of(0, pageSize);

        List<FollowEntity> relations;
        if (cursor == null) {
            relations = followRepository.findByFollowingIdAndStatusOrderByIdDesc(user.getId(), FollowStatus.approved, pr);
        } else {
            relations = followRepository.findByFollowingIdAndStatusAndIdLessThanOrderByIdDesc(user.getId(), FollowStatus.approved, cursor, pr);
        }

        List<String> followerIds = relations.stream().map(FollowEntity::getFollowerId).collect(Collectors.toList());
        List<UserEntity> users = followerIds.isEmpty() ? java.util.Collections.emptyList() : profileService.getUsersByIds(followerIds);

        final java.util.Set<String> alsoFollowingSet;
        if (viewerId != null && !viewerId.isBlank() && !followerIds.isEmpty()) {
            var rels = followRepository.findByFollowerIdAndFollowingIdIn(viewerId, followerIds);
            alsoFollowingSet = rels.stream()
                    .filter(r -> r.getStatus() == FollowStatus.approved)
                    .map(FollowEntity::getFollowingId)
                    .collect(Collectors.toSet());
        } else {
            alsoFollowingSet = java.util.Collections.emptySet();
        }
        var data = users.stream().map(u -> java.util.Map.of(
                "username", u.getUsername(),
                "pfpUrl", u.getProfile() != null ? u.getProfile().getAvatarUrl() : null,
                "alsoFollowing", viewerId != null && alsoFollowingSet.contains(u.getId())
        )).collect(Collectors.toList());

        String nextCursor = relations.isEmpty() ? null : relations.get(relations.size() - 1).getId();
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("data", data);
        resp.put("nextCursor", nextCursor);
        return resp;
    }

    public java.util.Map<String, Object> getFollowing(String username, int limit, String cursor) {
        UserEntity user = profileService.getUserByUsername(username);
        if (user == null) {
            throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        int pageSize = Math.max(1, Math.min(limit, 50));
        PageRequest pr = PageRequest.of(0, pageSize);

        List<FollowEntity> relations;
        if (cursor == null) {
            relations = followRepository.findByFollowerIdAndStatusOrderByIdDesc(user.getId(), FollowStatus.approved, pr);
        } else {
            relations = followRepository.findByFollowerIdAndStatusAndIdLessThanOrderByIdDesc(user.getId(), FollowStatus.approved, cursor, pr);
        }

        List<String> followingIds = relations.stream().map(FollowEntity::getFollowingId).collect(Collectors.toList());
        var users = profileService.getUsersByIds(followingIds);
        var data = users.stream().map(u -> java.util.Map.of(
                "username", u.getUsername(),
                "pfpUrl", u.getProfile() != null ? u.getProfile().getAvatarUrl() : null
        )).collect(Collectors.toList());

        String nextCursor = relations.isEmpty() ? null : relations.get(relations.size() - 1).getId();
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("data", data);
        resp.put("nextCursor", nextCursor);
        return resp;
    }
}
