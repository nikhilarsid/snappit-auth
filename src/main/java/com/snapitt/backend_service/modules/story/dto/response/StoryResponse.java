package com.snapitt.backend_service.modules.story.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * StoryResponse - DTO for a single story
 *
 * Used in:
 * - GET /api/v1/stories/{storyId}
 * - POST /api/v1/stories (create response)
 * - GET /api/v1/stories/user/{username} (list items)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoryResponse {
    private String id;              // Story ID
    private String authorUsername;  // Author's username
    private String mediaUrl;        // Media URL
    private Instant createdAt;      // Creation timestamp
    private Instant expiresAt;      // Expiration timestamp
    private Boolean canDelete;      // True if viewer is the story author
}
