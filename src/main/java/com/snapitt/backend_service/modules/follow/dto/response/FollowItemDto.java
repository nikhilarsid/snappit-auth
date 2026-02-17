package com.snapitt.backend_service.modules.follow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class FollowItemDto {
    private String username;
    private String avatarUrl;        
    private Boolean alsoFollowing;
    private String viewerFollowStatus; // "approved", "pending", or "none"

    public FollowItemDto(String username, String avatarUrl) {
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.alsoFollowing = null;
        this.viewerFollowStatus = null;
    }

    public FollowItemDto(String username, String avatarUrl, Boolean alsoFollowing) {
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.alsoFollowing = alsoFollowing;
        this.viewerFollowStatus = null;
    }
}
