package com.social.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't send null fields (like Bio if restricted)
public class ProfileResponse {
    private String id;
    private String username;
    private String avatarUrl;
    private String name;
    private String bio;
    private Integer followersCount;
    private Integer followingCount;
    private Boolean isFollowing; // True, False, or Null (if own profile)
    private Boolean isOwnProfile;
    private Instant updatedAt;
}