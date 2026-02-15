package com.snapitt.backend_service.modules.profile.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {
    private String username;
    private String avatarUrl;
    private String bio;
    private String name;
    private Long followersCount;
    private Long followingCount;
    private Boolean isFollowing;
    private Instant createdAt;
    private Instant updatedAt;

    // Constructor for backward compatibility with GET endpoint
    public ProfileResponse(String username, String avatarUrl, String bio, Long followersCount, Long followingCount, Boolean isFollowing, Instant createdAt) {
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
        this.isFollowing = isFollowing;
        this.createdAt = createdAt;
    }
}
