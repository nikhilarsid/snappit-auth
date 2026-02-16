package com.snapitt.backend_service.modules.follow.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Paginated response for GET /follow/{username}/following
 * Includes list of users being followed and cursor for next page
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedFollowingResponse {
    private List<FollowItemDto> data;
    private String nextCursor;      // Continuation cursor for next page, null if no more pages
}
