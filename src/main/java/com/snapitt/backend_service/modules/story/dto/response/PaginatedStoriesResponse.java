package com.snapitt.backend_service.modules.story.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * PaginatedStoriesResponse - DTO for paginated story list
 *
 * Used in:
 * - GET /api/v1/stories/user/{username}
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedStoriesResponse {
    private List<StoryResponse> data;        // List of stories
    private String nextCursor;              // Cursor for next page (null if no more pages)
}
