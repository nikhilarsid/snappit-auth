package com.snapitt.backend_service.modules.feed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PaginatedFeedStoriesResponse - Response for GET /api/v1/feed/stories
 *
 * Provides cursor-paginated list of stories in user's feed.
 * Ordered by: unseen first (ascending), then latest stories first (descending).
 *
 * Fields:
 * - data: List of FeedStoryDto items
 * - nextCursor: Cursor for next page (null if no more pages)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedFeedStoriesResponse {
    private List<FeedStoryDto> data;
    private String nextCursor;
}
