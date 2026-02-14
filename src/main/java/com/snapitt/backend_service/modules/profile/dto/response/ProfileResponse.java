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
    private Long followersCount;
    private Long followingCount;
    private Boolean isFollowing;
    private Instant createdAt;
}
