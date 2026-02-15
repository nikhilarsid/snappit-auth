package com.snapitt.backend_service.modules.comment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * PaginatedCommentsResponse - DTO for paginated comment list
 *
 * Used in:
 * - GET /api/v1/posts/{postId}/comments
 * - GET /api/v1/comments/{commentId}/replies
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedCommentsResponse {
    private List<CommentResponse> data;      // List of comments
    private String nextCursor;               // Cursor for next page (null if no more pages)
}
