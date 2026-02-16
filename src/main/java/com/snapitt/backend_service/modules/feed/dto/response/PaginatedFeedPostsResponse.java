package com.snapitt.backend_service.modules.feed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PaginatedFeedPostsResponse - Response for GET /api/v1/feed/posts
 *
 * Provides cursor-paginated list of posts in user's feed.
 * Ordered by: unseen first (ascending), then freshest posts first (descending by createdAt).
 *
 * Fields:
 * - data: List of FeedPostDto items
 * - nextCursor: Cursor for next page (null if no more pages)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedFeedPostsResponse {
    private List<FeedPostDto> data;
    private String nextCursor;
}
