package com.snapitt.backend_service.modules.post.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * PaginatedPostsResponse - DTO for paginated post list
 *
 * Used in:
 * - GET /api/v1/posts/user/{username}
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedPostsResponse {
    private List<PostResponse> data;        // List of posts
    private String nextCursor;              // Cursor for next page (null if no more pages)
}
