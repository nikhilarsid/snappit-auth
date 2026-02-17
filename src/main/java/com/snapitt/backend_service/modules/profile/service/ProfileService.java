package com.snapitt.backend_service.modules.profile.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.dto.request.UpdateProfileRequest;
import com.snapitt.backend_service.modules.profile.dto.response.ProfileResponse;
import com.snapitt.backend_service.modules.story.repository.StoryRepository;
import com.snapitt.backend_service.modules.feed.repository.StoryFeedRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final StoryRepository storyRepository;
    private final StoryFeedRepository storyFeedRepository;

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_BIO_LENGTH = 160;
    private static final int MIN_NAME_LENGTH = 1;
    private static final int MIN_BIO_LENGTH = 1;

    public ProfileResponse getProfile(String viewerId, String username) {
        try {
            UserEntity targetUser = userRepository.findByUsername(username)
                    .orElseThrow(() -> new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

            boolean isFollowing = false;
            boolean canViewFullProfile = false;
            String followStatus = "none";

            if (viewerId != null) {
                if (viewerId.equals(targetUser.getId())) {
                    canViewFullProfile = true;
                } else {
                    var relOpt = followRepository.findByFollowerIdAndFollowingId(viewerId, targetUser.getId());
                    if (relOpt.isPresent()) {
                        String status = String.valueOf(relOpt.get().getStatus());
                        if ("approved".equalsIgnoreCase(status)) {
                            isFollowing = true;
                            canViewFullProfile = true;
                            followStatus = "approved";
                        } else if ("pending".equalsIgnoreCase(status)) {
                            followStatus = "pending";
                        }
                    }
                }
            }

            ProfileResponse response = buildProfileResponse(targetUser, canViewFullProfile, isFollowing, viewerId);
            response.setFollowStatus(followStatus);
            return response;
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving profile for username: {}", username, ex);
            throw new AuthException("An error occurred while retrieving profile", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public UserEntity getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElse(null);
    }

    public UserEntity getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    public ProfileResponse getMyProfile(String userId) {
        try {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new AuthException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

            return buildProfileResponse(user, true, false, userId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving own profile for userId: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving profile", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public java.util.List<com.snapitt.backend_service.modules.user.model.UserEntity> getUsersByIds(java.util.List<String> ids) {
        return userRepository.findAllById(ids);
    }

    private ProfileResponse buildProfileResponse(UserEntity user, boolean canViewFull, boolean isFollowing, String viewerId) {
        String bio = null;
        String name = null;
        String avatarUrl = null;
        if (user.getProfile() != null) {
            bio = user.getProfile().getBio();
            name = user.getProfile().getName();
            avatarUrl = user.getProfile().getAvatarUrl();
        }

        boolean hasStory = canViewFull && storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(
                user.getId(), java.time.Instant.now());

        Boolean storySeenByViewer = null;
        if (hasStory && viewerId != null) {
            if (viewerId.equals(user.getId())) {
                
                storySeenByViewer = true;
            } else {
                storySeenByViewer = storyFeedRepository
                        .findByUserIdAndCreatorIdAndIsDeletedFalse(viewerId, user.getId())
                        .map(feed -> Boolean.TRUE.equals(feed.getSeen()))
                        .orElse(false);
            }
        }

        ProfileResponse response = new ProfileResponse(
                user.getUsername(),
                avatarUrl,
                bio,
                user.getFollowersCount(),
                user.getFollowingCount(),
                user.getPostCount() != null ? user.getPostCount() : 0L,
                isFollowing,
                hasStory,
                user.getCreatedAt()
        );
        response.setName(name);
        response.setStorySeenByViewer(storySeenByViewer);
        return response;
    }

    public ProfileResponse updateProfile(String userId, UpdateProfileRequest update) {
        try {
            
            if ((update.getName() == null || update.getName().isBlank())
                    && (update.getBio() == null || update.getBio().isBlank())
                    && (update.getAvatarUrl() == null || update.getAvatarUrl().isBlank())) {
                throw new AuthException("No updatable fields provided", "NO_VALID_FIELDS", HttpStatus.BAD_REQUEST);
            }

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new AuthException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (user.getProfile() == null) {
                user.setProfile(UserEntity.Profile.builder().build());
            }

            if (update.getName() != null && !update.getName().isBlank()) {
                user.getProfile().setName(update.getName());
            }
            if (update.getBio() != null && !update.getBio().isBlank()) {
                user.getProfile().setBio(update.getBio());
            }
            if (update.getAvatarUrl() != null && !update.getAvatarUrl().isBlank()) {
                String url = update.getAvatarUrl().trim();
                // Allow internal uploads (/uploads/...) and valid http(s) URLs only
                if (!url.startsWith("/uploads/")) {
                    try {
                        java.net.URI uri = new java.net.URI(url);
                        String scheme = uri.getScheme();
                        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                            throw new AuthException("Avatar URL must use http or https", "INVALID_URL", HttpStatus.BAD_REQUEST);
                        }
                    } catch (java.net.URISyntaxException e) {
                        throw new AuthException("Invalid avatar URL format", "INVALID_URL", HttpStatus.BAD_REQUEST);
                    }
                }
                user.getProfile().setAvatarUrl(url);
            }

            user.setUpdatedAt(Instant.now());

            UserEntity saved = userRepository.save(user);

            String avatarUrl = saved.getProfile() != null ? saved.getProfile().getAvatarUrl() : null;
            String bio = saved.getProfile() != null ? saved.getProfile().getBio() : null;
            String name = saved.getProfile() != null ? saved.getProfile().getName() : null;

            boolean hasStory = storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(
                    saved.getId(), Instant.now());

            ProfileResponse response = new ProfileResponse(
                    saved.getUsername(),
                    avatarUrl,
                    bio,
                    saved.getFollowersCount(),
                    saved.getFollowingCount(),
                    saved.getPostCount() != null ? saved.getPostCount() : 0L,
                    false,
                    hasStory,
                    saved.getUpdatedAt()
            );
            response.setName(name);
            return response;
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error updating profile for userId: {}", userId, ex);
            throw new AuthException("An error occurred while updating profile", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
