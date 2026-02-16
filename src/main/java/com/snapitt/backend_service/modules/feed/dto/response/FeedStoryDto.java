package com.snapitt.backend_service.modules.feed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * FeedStoryDto - DTO for a story item in user's feed
 *
 * Represents one story creator's latest story in the user's story feed.
 * Used in GET /api/v1/feed/stories response.
 *
 * Fields:
 * - id: Story feed record ID (story_feed document _id) - for marking as seen
 * - username: Story creator's username
 * - avatar_url: Creator's profile avatar URL
 * - story_id: ID of the latest story from this creator
 * - seen: Whether user has viewed the latest story
 * - latest_story_at: Timestamp of the latest story
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedStoryDto {
    private String feedStoryId;       // Story feed record ID (for marking as seen)
    private String creatorUsername;    // Story creator's username
    private String creatorAvatarUrl;  // Creator's avatar URL
    private String storyId;           // ID of latest story from this creator
    private Boolean seen;             // Whether user has seen the latest story
    private Instant latestStoryAt;    // Timestamp of latest story
}
