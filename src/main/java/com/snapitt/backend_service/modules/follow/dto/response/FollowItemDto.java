package com.snapitt.backend_service.modules.follow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for individual follower/following list items
 * Used in paginated responses for GET /follow/{username}/followers
 * and GET /follow/{username}/following
 */
@Data
@Builder
@AllArgsConstructor
public class FollowItemDto {
    private String username;
    private String pfpUrl;           // Profile picture URL
    private Boolean alsoFollowing;   // Whether viewer is following this user (optional, only in followers list)

    /**
     * Constructor for following list (no alsoFollowing flag)
     */
    public FollowItemDto(String username, String pfpUrl) {
        this.username = username;
        this.pfpUrl = pfpUrl;
        this.alsoFollowing = null;
    }
}
