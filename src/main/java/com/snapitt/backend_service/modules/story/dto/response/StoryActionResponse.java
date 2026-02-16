package com.snapitt.backend_service.modules.story.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoryActionResponse {
    private String message;

    public static StoryActionResponse of(String message) {
        return new StoryActionResponse(message);
    }
}
