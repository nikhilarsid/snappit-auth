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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FollowService - Handles follow operations and business logic
 *
 * Business Logic Validations (not in DTOs):
 * - Self-follow prevention
 * - Existing relationship checks
 * - Status conflict checks
 *
 * Exception Handling:
 * - AuthException: All business logic exceptions
 * - Generic Exception: Caught and wrapped as INTERNAL_SERVER_ERROR
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private static final Logger logger = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final ProfileService profileService;
    private final EventService eventService;

    /**
     * Create follow request to target user
     * @throws AuthException (USER_NOT_FOUND, 404) - Target user not found
     * @throws AuthException (CANNOT_FOLLOW_SELF, 400) - Cannot follow self
     * @throws AuthException (ALREADY_FOLLOWING, 409) - Already following
     * @throws AuthException (REQUEST_ALREADY_SENT, 409) - Request already exists
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
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

            // Emit FOLLOW_REQUESTED event (transactional - allow rollback on failure)
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

    /**
     * Approve follow request from user
     * @throws AuthException (USER_NOT_FOUND, 404) - Requester not found
     * @throws AuthException (NO_PENDING_REQUEST, 404) - No pending request found
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
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

    /**
     * Reject follow request from user
     * @throws AuthException (USER_NOT_FOUND, 404) - Requester not found
     * @throws AuthException (NO_PENDING_REQUEST, 404) - No pending request found
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
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

    /**
     * Unfollow a user
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (CANNOT_UNFOLLOW_SELF, 400) - Cannot unfollow self
     * @throws AuthException (NOT_FOLLOWING, 404) - Not currently following
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public void unfollow(String followerId, String targetUsername) {
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
            if (relation == null || relation.getStatus() != FollowStatus.approved) {
                throw new AuthException("Not following", "NOT_FOLLOWING", HttpStatus.NOT_FOUND);
            }

            String relationId = relation.getId();
            followRepository.deleteByFollowerIdAndFollowingId(followerId, targetUser.getId());

            eventService.emitEvent(EventType.UNFOLLOWED, targetUser.getId(), Map.of(
                "followerId", followerId,
                "followingId", targetUser.getId(),
                "relationId", relationId
            ));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error unfollowing user {} from {}", targetUsername, followerId, ex);
            throw new AuthException("An error occurred while unfollowing", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get followers of a user (paginated)
     * @return PaginatedFollowersResponse with follower list and nextCursor
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
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

            final Set<String> alsoFollowingSet;
            if (viewerId != null && !viewerId.isBlank() && !followerIds.isEmpty()) {
                var rels = followRepository.findByFollowerIdAndFollowingIdIn(viewerId, followerIds);
                alsoFollowingSet = rels.stream()
                        .filter(r -> r.getStatus() == FollowStatus.approved)
                        .map(FollowEntity::getFollowingId)
                        .collect(Collectors.toSet());
            } else {
                alsoFollowingSet = java.util.Collections.emptySet();
            }

            List<FollowItemDto> data = users.stream()
                    .map(u -> new FollowItemDto(
                            u.getUsername(),
                            u.getProfile() != null ? u.getProfile().getAvatarUrl() : null,
                            viewerId != null && alsoFollowingSet.contains(u.getId())
                    ))
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

    /**
     * Get following list of a user (paginated)
     * @return PaginatedFollowingResponse with following list and nextCursor
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
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

            // Check which users the viewer also follows
            final Set<String> viewerFollowingSet;
            if (viewerId != null && !viewerId.isBlank() && !followingIds.isEmpty()) {
                var viewerRels = followRepository.findByFollowerIdAndFollowingIdIn(viewerId, followingIds);
                viewerFollowingSet = viewerRels.stream()
                        .filter(r -> r.getStatus() == FollowStatus.approved)
                        .map(FollowEntity::getFollowingId)
                        .collect(Collectors.toSet());
            } else {
                viewerFollowingSet = java.util.Collections.emptySet();
            }

            List<FollowItemDto> data = users.stream()
                    .map(u -> new FollowItemDto(
                            u.getUsername(),
                            u.getProfile() != null ? u.getProfile().getAvatarUrl() : null,
                            viewerId != null && viewerFollowingSet.contains(u.getId())
                    ))
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

    /**
     * Get followers by userId (paginated) - avoids username lookup
     */
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

            final Set<String> alsoFollowingSet;
            if (viewerId != null && !viewerId.isBlank() && !followerIds.isEmpty()) {
                var rels = followRepository.findByFollowerIdAndFollowingIdIn(viewerId, followerIds);
                alsoFollowingSet = rels.stream()
                        .filter(r -> r.getStatus() == FollowStatus.approved)
                        .map(FollowEntity::getFollowingId)
                        .collect(Collectors.toSet());
            } else {
                alsoFollowingSet = java.util.Collections.emptySet();
            }

            List<FollowItemDto> data = users.stream()
                    .map(u -> new FollowItemDto(
                            u.getUsername(),
                            u.getProfile() != null ? u.getProfile().getAvatarUrl() : null,
                            viewerId != null && alsoFollowingSet.contains(u.getId())
                    ))
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

    /**
     * Get pending follow requests for the authenticated user (paginated)
     * Returns users who have sent a follow request to this user that is still pending.
     */
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

            List<FollowItemDto> data = users.stream()
                    .map(u -> new FollowItemDto(
                            u.getUsername(),
                            u.getProfile() != null ? u.getProfile().getAvatarUrl() : null,
                            false  // not following since this is a pending request
                    ))
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

    /**
     * Get following by userId (paginated) - avoids username lookup
     */
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

            // Check which users the viewer also follows
            final Set<String> viewerFollowingSet;
            if (viewerId != null && !viewerId.isBlank() && !followingIds.isEmpty()) {
                var viewerRels = followRepository.findByFollowerIdAndFollowingIdIn(viewerId, followingIds);
                viewerFollowingSet = viewerRels.stream()
                        .filter(r -> r.getStatus() == FollowStatus.approved)
                        .map(FollowEntity::getFollowingId)
                        .collect(Collectors.toSet());
            } else {
                viewerFollowingSet = java.util.Collections.emptySet();
            }

            List<FollowItemDto> data = users.stream()
                    .map(u -> new FollowItemDto(
                            u.getUsername(),
                            u.getProfile() != null ? u.getProfile().getAvatarUrl() : null,
                            viewerId != null && viewerFollowingSet.contains(u.getId())
                    ))
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
}
