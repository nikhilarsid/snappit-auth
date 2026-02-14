package com.snapitt.backend_service.modules.profile.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.dto.request.UpdateProfileRequest;
import com.snapitt.backend_service.modules.profile.dto.response.ProfileResponse;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    public ProfileResponse getProfile(String viewerId, String username) {
        UserEntity targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

        boolean isFollowing = false;
        boolean canViewFullProfile = false;

        if (viewerId != null) {
            if (viewerId.equals(targetUser.getId())) {
                canViewFullProfile = true;
            } else {
                followRepository.findByFollowerIdAndFollowingId(viewerId, targetUser.getId()).ifPresent(relation -> {
                    if ("approved".equalsIgnoreCase(String.valueOf(relation.getStatus()))) {
                        // set flags via side effects on local variables by capturing via final wrapper is inconvenient; instead mutate local via closure not allowed.
                    }
                });
                // Simpler: query optional then inspect
                var relOpt = followRepository.findByFollowerIdAndFollowingId(viewerId, targetUser.getId());
                if (relOpt.isPresent() && "approved".equalsIgnoreCase(String.valueOf(relOpt.get().getStatus()))) {
                    isFollowing = true;
                    canViewFullProfile = true;
                }
            }
        }

        return buildProfileResponse(targetUser, canViewFullProfile, isFollowing);
    }

    // Resolve username -> UserEntity (used by follow service per design)
    public UserEntity getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElse(null);
    }

    // Given list of ids return minimal user objects (username mapping) used by follow pagination
    public java.util.List<com.snapitt.backend_service.modules.user.model.UserEntity> getUsersByIds(java.util.List<String> ids) {
        return userRepository.findAllById(ids);
    }

    private ProfileResponse buildProfileResponse(UserEntity user, boolean canViewFull, boolean isFollowing) {
        String bio = null;
        if (canViewFull && user.getProfile() != null) {
            bio = user.getProfile().getBio();
        }

        String avatarUrl = null;
        if (user.getProfile() != null) {
            avatarUrl = user.getProfile().getAvatarUrl();
        }

        return new ProfileResponse(
                user.getUsername(),
                avatarUrl,
                bio,
                user.getFollowersCount(),
                user.getFollowingCount(),
                isFollowing,
                user.getCreatedAt()
        );
    }

    public ProfileResponse updateProfile(String userId, UpdateProfileRequest update) {
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

        if (update.getName() != null) {
            user.getProfile().setName(update.getName());
        }
        if (update.getBio() != null) {
            user.getProfile().setBio(update.getBio());
        }
        if (update.getAvatarUrl() != null) {
            user.getProfile().setAvatarUrl(update.getAvatarUrl());
        }

        user.setUpdatedAt(Instant.now());

        UserEntity saved = userRepository.save(user);

        String avatarUrl = saved.getProfile() != null ? saved.getProfile().getAvatarUrl() : null;
        String bio = saved.getProfile() != null ? saved.getProfile().getBio() : null;

        return new ProfileResponse(saved.getUsername(), avatarUrl, bio, saved.getFollowersCount(), saved.getFollowingCount(), false, saved.getUpdatedAt());
    }
}
