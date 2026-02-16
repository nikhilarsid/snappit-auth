package com.snapitt.backend_service.modules.follow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FollowResponse {
    private String message;

    public static FollowResponse of(String message) {
        return new FollowResponse(message);
    }
}
