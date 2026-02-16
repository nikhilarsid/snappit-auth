package com.snapitt.backend_service.modules.follow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Simple response DTO for follow action endpoints
 * Used for: POST /follow/{username}, POST /follow/{username}/approve,
 * POST /follow/{username}/reject, DELETE /follow/{username}
 */
@Data
@AllArgsConstructor
public class FollowResponse {
    private String message;

    public static FollowResponse of(String message) {
        return new FollowResponse(message);
    }
}
