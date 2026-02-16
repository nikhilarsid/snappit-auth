package com.snapitt.backend_service.modules.post.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PostActionResponse {
    private String message;

    public static PostActionResponse of(String message) {
        return new PostActionResponse(message);
    }
}
