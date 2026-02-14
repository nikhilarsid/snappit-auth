package com.social.profile.service;

import com.social.profile.dto.ProfileResponse;
import com.social.profile.dto.UpdateProfileRequest;
import com.social.profile.entity.User;
import com.social.profile.exception.UserNotFoundException;
import com.social.profile.repository.UserRepository;
import com.social.profile.service.client.FollowServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final FollowServiceClient followServiceClient;

    // 1️⃣ GET PROFILE
    public ProfileResponse getProfile(String username, String viewerId) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        boolean isOwnProfile = viewerId != null && viewerId.equals(targetUser.getId());
        boolean isFollowing = false;

        // Determine Follow Status if not looking at own profile
        if (!isOwnProfile && viewerId != null) {
            isFollowing = followServiceClient.isFollowing(viewerId, targetUser.getId());
        }

        // Access Control: Can view full profile?
        // (Assuming public profiles for now, but you can limit bio here if needed)
        boolean canViewFullProfile = isOwnProfile || isFollowing;

        return ProfileResponse.builder()
                .username(targetUser.getUsername())
                .avatarUrl(targetUser.getProfile().getAvatarUrl())
                .name(targetUser.getProfile().getName())
                .followersCount(targetUser.getFollowersCount())
                .followingCount(targetUser.getFollowingCount())
                // Only show Bio if allowed (example logic)
                .bio(targetUser.getProfile().getBio())
                .isFollowing(isOwnProfile ? null : isFollowing)
                .isOwnProfile(isOwnProfile)
                .build();
    }

    // 2️⃣ UPDATE PROFILE
    public ProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // PATCH Logic: Only update non-null fields
        if (request.getName() != null) user.getProfile().setName(request.getName());
        if (request.getBio() != null) user.getProfile().setBio(request.getBio());
        if (request.getAvatarUrl() != null) user.getProfile().setAvatarUrl(request.getAvatarUrl());

        User updatedUser = userRepository.save(user);

        return ProfileResponse.builder()
                .username(updatedUser.getUsername())
                .avatarUrl(updatedUser.getProfile().getAvatarUrl())
                .name(updatedUser.getProfile().getName())
                .bio(updatedUser.getProfile().getBio())
                .updatedAt(updatedUser.getUpdatedAt()) // Ensure Entity has field or handle in DTO
                .build();
    }
}