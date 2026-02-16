package com.snapitt.backend_service.modules.post.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * PostActionResponse - DTO for post action responses (delete, like, unlike)
 *
 * Used in:
 * - DELETE /api/v1/posts/{postId} (delete post)
 * - POST /api/v1/posts/{postId}/like (like post)
 * - DELETE /api/v1/posts/{postId}/like (unlike post)
 */
@Data
@AllArgsConstructor
public class PostActionResponse {
    private String message;

    public static PostActionResponse of(String message) {
        return new PostActionResponse(message);
    }
}
