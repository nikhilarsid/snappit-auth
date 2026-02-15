package com.snapitt.backend_service.modules.comment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CommentActionResponse - DTO for comment action responses (delete)
 *
 * Used in:
 * - DELETE /api/v1/comments/{commentId} (delete comment)
 */
@Data
@AllArgsConstructor
public class CommentActionResponse {
    private String message;

    public static CommentActionResponse of(String message) {
        return new CommentActionResponse(message);
    }
}
