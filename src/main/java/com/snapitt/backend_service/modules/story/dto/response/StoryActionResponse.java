package com.snapitt.backend_service.modules.story.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * StoryActionResponse - DTO for story action responses (delete)
 *
 * Used in:
 * - DELETE /api/v1/stories/{storyId} (delete story)
 */
@Data
@AllArgsConstructor
public class StoryActionResponse {
    private String message;

    public static StoryActionResponse of(String message) {
        return new StoryActionResponse(message);
    }
}
