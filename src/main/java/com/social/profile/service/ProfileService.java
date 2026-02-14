package com.social.profile.service;

import com.social.profile.dto.ProfileResponse;
import com.social.profile.dto.UpdateProfileRequest;
import com.social.profile.entity.User;
import com.social.profile.exception.UserNotFoundException;
import com.social.profile.repository.UserRepository;
import com.social.profile.service.client.FollowServiceClient; // Ensure this exists or remove if mocking
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    // private final FollowServiceClient followServiceClient; // Uncomment when Client is ready

    // ✅ NEW: Search Logic
    public List<ProfileResponse> searchProfiles(String query) {
        // 1. Call the custom repository method (Atlas Search)
        List<User> searchResults = userRepository.searchUsers(query);

        // 2. Map User entities to simple ProfileResponse DTOs
        return searchResults.stream()
                .map(user -> ProfileResponse.builder()
                        .id(user.getId()) // Ensure DTO has ID field
                        .username(user.getUsername())
                        .name(user.getProfile().getName())
                        .avatarUrl(user.getProfile().getAvatarUrl())
                        .bio(user.getProfile().getBio())
                        .followersCount(user.getFollowersCount())
                        // For search results, we usually don't calculate "isFollowing"
                        // for every single result to keep it fast.
                        .isFollowing(false)
                        .isOwnProfile(false)
                        .build())
                .collect(Collectors.toList());
    }

    // 1️⃣ GET PROFILE
    public ProfileResponse getProfile(String username, String viewerId) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        boolean isOwnProfile = viewerId != null && viewerId.equals(targetUser.getId());
        boolean isFollowing = false;

        // Determine Follow Status if not looking at own profile
        if (!isOwnProfile && viewerId != null) {
            // isFollowing = followServiceClient.isFollowing(viewerId, targetUser.getId());
            // TODO: Uncomment above line when FollowService is connected
        }

        return ProfileResponse.builder()
                .id(targetUser.getId())
                .username(targetUser.getUsername())
                .avatarUrl(targetUser.getProfile().getAvatarUrl())
                .name(targetUser.getProfile().getName())
                .followersCount(targetUser.getFollowersCount())
                .followingCount(targetUser.getFollowingCount())
                .bio(targetUser.getProfile().getBio())
                .isFollowing(isFollowing)
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
                .id(updatedUser.getId())
                .username(updatedUser.getUsername())
                .avatarUrl(updatedUser.getProfile().getAvatarUrl())
                .name(updatedUser.getProfile().getName())
                .bio(updatedUser.getProfile().getBio())
                .isOwnProfile(true) // Always true for update response
                //.updatedAt(updatedUser.getUpdatedAt()) // Ensure DTO supports this
                .build();
    }
}