package com.snapitt.backend_service.modules.comment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommentActionResponse {
    private String message;

    public static CommentActionResponse of(String message) {
        return new CommentActionResponse(message);
    }
}
