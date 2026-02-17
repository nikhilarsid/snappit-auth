package com.snapitt.backend_service.modules.follow.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.dto.response.FollowItemDto;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowersResponse;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowingResponse;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final Logger logger = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final ProfileService profileService;
    private final EventService eventService;

    @Transactional
    public void createFollowRequest(String followerId, String targetUsername) {
        try {
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

            eventService.emitEvent(EventType.FOLLOW_REQUESTED, targetUser.getId(), Map.of(
                "followerId", followerId,
                "followingId", targetUser.getId(),
                "relationId", saved.getId()
            ));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error creating follow request from {} to {}", followerId, targetUsername, ex);
            throw new AuthException("An error occurred while creating follow request", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void approveFollow(String currentUserId, String followerUsername) {
        try {
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
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error approving follow request from {} for user {}", followerUsername, currentUserId, ex);
            throw new AuthException("An error occurred while approving follow request", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void rejectFollow(String currentUserId, String followerUsername) {
        try {
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
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error rejecting follow request from {} for user {}", followerUsername, currentUserId, ex);
            throw new AuthException("An error occurred while rejecting follow request", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public String unfollow(String followerId, String targetUsername) {
        try {
            UserEntity targetUser = profileService.getUserByUsername(targetUsername);
            if (targetUser == null) {
                throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            if (targetUser.getId() != null && targetUser.getId().equals(followerId)) {
                throw new AuthException("Cannot unfollow self", "CANNOT_UNFOLLOW_SELF", HttpStatus.BAD_REQUEST);
            }

            var relationOpt = followRepository.findByFollowerIdAndFollowingId(followerId, targetUser.getId());
            var relation = relationOpt.orElse(null);
            if (relation == null || (relation.getStatus() != FollowStatus.approved && relation.getStatus() != FollowStatus.pending)) {
                throw new AuthException("Not following", "NOT_FOLLOWING", HttpStatus.NOT_FOUND);
            }

            boolean wasPending = relation.getStatus() == FollowStatus.pending;
            String relationId = relation.getId();
            followRepository.deleteByFollowerIdAndFollowingId(followerId, targetUser.getId());

            if (wasPending) {
                eventService.emitEvent(EventType.FOLLOW_REQUEST_WITHDRAWN, targetUser.getId(), Map.of(
                    "followerId", followerId,
                    "followingId", targetUser.getId(),
                    "relationId", relationId
                ));
                return "FOLLOW_REQUEST_WITHDRAWN";
            } else {
                eventService.emitEvent(EventType.UNFOLLOWED, targetUser.getId(), Map.of(
                    "followerId", followerId,
                    "followingId", targetUser.getId(),
                    "relationId", relationId
                ));
                return "UNFOLLOWED";
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error unfollowing user {} from {}", targetUsername, followerId, ex);
            throw new AuthException("An error occurred while unfollowing", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedFollowersResponse getFollowers(String username, int limit, String cursor, String viewerId) {
        try {
            UserEntity user = profileService.getUserByUsername(username);
            if (user == null) {
                throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pr = PageRequest.of(0, pageSize);

            List<FollowEntity> relations;
            if (cursor == null) {
                relations = followRepository.findByFollowingIdAndStatusOrderByIdDesc(user.getId(), FollowStatus.approved, pr);
            } else {
                relations = followRepository.findByFollowingIdAndStatusAndIdLessThanOrderByIdDesc(user.getId(), FollowStatus.approved, cursor, pr);
            }

            List<String> followerIds = relations.stream()
                    .map(FollowEntity::getFollowerId)
                    .collect(Collectors.toList());
            
            List<UserEntity> users = followerIds.isEmpty() ? java.util.Collections.emptyList() : profileService.getUsersByIds(followerIds);

            final Map<String, String> viewerStatusMap = buildViewerFollowStatusMap(viewerId, followerIds);

            List<FollowItemDto> data = users.stream()
                    .map(u -> FollowItemDto.builder()
                            .username(u.getUsername())
                            .avatarUrl(u.getProfile() != null ? u.getProfile().getAvatarUrl() : null)
                            .alsoFollowing("approved".equals(viewerStatusMap.get(u.getId())))
                            .viewerFollowStatus(viewerStatusMap.getOrDefault(u.getId(), "none"))
                            .build())
                    .collect(Collectors.toList());

            String nextCursor = relations.isEmpty() ? null : relations.get(relations.size() - 1).getId();
            
            return PaginatedFollowersResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving followers for username: {}", username, ex);
            throw new AuthException("An error occurred while retrieving followers", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedFollowingResponse getFollowing(String username, int limit, String cursor, String viewerId) {
        try {
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

            List<String> followingIds = relations.stream()
                    .map(FollowEntity::getFollowingId)
                    .collect(Collectors.toList());
            
            List<UserEntity> users = followingIds.isEmpty() ? java.util.Collections.emptyList() : profileService.getUsersByIds(followingIds);

            final Map<String, String> viewerStatusMap = buildViewerFollowStatusMap(viewerId, followingIds);

            List<FollowItemDto> data = users.stream()
                    .map(u -> FollowItemDto.builder()
                            .username(u.getUsername())
                            .avatarUrl(u.getProfile() != null ? u.getProfile().getAvatarUrl() : null)
                            .alsoFollowing("approved".equals(viewerStatusMap.get(u.getId())))
                            .viewerFollowStatus(viewerStatusMap.getOrDefault(u.getId(), "none"))
                            .build())
                    .collect(Collectors.toList());

            String nextCursor = relations.isEmpty() ? null : relations.get(relations.size() - 1).getId();
            
            return PaginatedFollowingResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving following for username: {}", username, ex);
            throw new AuthException("An error occurred while retrieving following", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedFollowersResponse getFollowersByUserId(String userId, int limit, String cursor, String viewerId) {
        try {
            UserEntity user = profileService.getUserById(userId);
            if (user == null) {
                throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pr = PageRequest.of(0, pageSize);

            List<FollowEntity> relations;
            if (cursor == null) {
                relations = followRepository.findByFollowingIdAndStatusOrderByIdDesc(user.getId(), FollowStatus.approved, pr);
            } else {
                relations = followRepository.findByFollowingIdAndStatusAndIdLessThanOrderByIdDesc(user.getId(), FollowStatus.approved, cursor, pr);
            }

            List<String> followerIds = relations.stream()
                    .map(FollowEntity::getFollowerId)
                    .collect(Collectors.toList());

            List<UserEntity> users = followerIds.isEmpty() ? java.util.Collections.emptyList() : profileService.getUsersByIds(followerIds);

            final Map<String, String> viewerStatusMap = buildViewerFollowStatusMap(viewerId, followerIds);

            List<FollowItemDto> data = users.stream()
                    .map(u -> FollowItemDto.builder()
                            .username(u.getUsername())
                            .avatarUrl(u.getProfile() != null ? u.getProfile().getAvatarUrl() : null)
                            .alsoFollowing("approved".equals(viewerStatusMap.get(u.getId())))
                            .viewerFollowStatus(viewerStatusMap.getOrDefault(u.getId(), "none"))
                            .build())
                    .collect(Collectors.toList());

            String nextCursor = relations.isEmpty() ? null : relations.get(relations.size() - 1).getId();

            return PaginatedFollowersResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving followers for userId: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving followers", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedFollowersResponse getPendingFollowRequests(String userId, int limit, String cursor) {
        try {
            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pr = PageRequest.of(0, pageSize);

            List<FollowEntity> relations;
            if (cursor == null) {
                relations = followRepository.findByFollowingIdAndStatusOrderByIdDesc(userId, FollowStatus.pending, pr);
            } else {
                relations = followRepository.findByFollowingIdAndStatusAndIdLessThanOrderByIdDesc(userId, FollowStatus.pending, cursor, pr);
            }

            List<String> followerIds = relations.stream()
                    .map(FollowEntity::getFollowerId)
                    .collect(Collectors.toList());

            List<UserEntity> users = followerIds.isEmpty() ? java.util.Collections.emptyList() : profileService.getUsersByIds(followerIds);

            // Check if the current user already follows each requester
            final Map<String, String> viewerStatusMap = buildViewerFollowStatusMap(userId, followerIds);

            List<FollowItemDto> data = users.stream()
                    .map(u -> FollowItemDto.builder()
                            .username(u.getUsername())
                            .avatarUrl(u.getProfile() != null ? u.getProfile().getAvatarUrl() : null)
                            .alsoFollowing("approved".equals(viewerStatusMap.get(u.getId())))
                            .viewerFollowStatus(viewerStatusMap.getOrDefault(u.getId(), "none"))
                            .build())
                    .collect(Collectors.toList());

            String nextCursor = relations.isEmpty() ? null : relations.get(relations.size() - 1).getId();

            return PaginatedFollowersResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving pending follow requests for userId: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving pending follow requests", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedFollowingResponse getFollowingByUserId(String userId, int limit, String cursor, String viewerId) {
        try {
            UserEntity user = profileService.getUserById(userId);
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

            List<String> followingIds = relations.stream()
                    .map(FollowEntity::getFollowingId)
                    .collect(Collectors.toList());

            List<UserEntity> users = followingIds.isEmpty() ? java.util.Collections.emptyList() : profileService.getUsersByIds(followingIds);

            final Map<String, String> viewerStatusMap = buildViewerFollowStatusMap(viewerId, followingIds);

            List<FollowItemDto> data = users.stream()
                    .map(u -> FollowItemDto.builder()
                            .username(u.getUsername())
                            .avatarUrl(u.getProfile() != null ? u.getProfile().getAvatarUrl() : null)
                            .alsoFollowing("approved".equals(viewerStatusMap.get(u.getId())))
                            .viewerFollowStatus(viewerStatusMap.getOrDefault(u.getId(), "none"))
                            .build())
                    .collect(Collectors.toList());

            String nextCursor = relations.isEmpty() ? null : relations.get(relations.size() - 1).getId();

            return PaginatedFollowingResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving following for userId: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving following", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Build a map of targetUserId -> follow status ("approved", "pending", or absent)
     * for the viewer's follow relationships to the given user IDs.
     */
    private Map<String, String> buildViewerFollowStatusMap(String viewerId, List<String> targetUserIds) {
        if (viewerId == null || viewerId.isBlank() || targetUserIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        var rels = followRepository.findByFollowerIdAndFollowingIdIn(viewerId, targetUserIds);
        Map<String, String> statusMap = new HashMap<>();
        for (FollowEntity rel : rels) {
            if (rel.getStatus() == FollowStatus.approved) {
                statusMap.put(rel.getFollowingId(), "approved");
            } else if (rel.getStatus() == FollowStatus.pending) {
                statusMap.put(rel.getFollowingId(), "pending");
            }
        }
        return statusMap;
    }
}
