package com.snapitt.backend_service.modules.feed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * FeedPostDto - DTO for a post item in user's feed
 *
 * Represents a post in the user's feed along with feed metadata.
 * Used in GET /api/v1/feed/posts response.
 *
 * Fields:
 * - id: Post feed record ID (post_feed document _id) - for marking as seen
 * - post: Complete post object with author, content, likes, etc.
 * - seen: Whether user has viewed this post
 * - createdAt: Post creation timestamp (for ordering)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedPostDto {
    private String id;               // Post feed record ID (for marking as seen)
    private PostResponse post;       // Complete post object
    private Boolean seen;            // Whether user has seen this post
    private Instant createdAt;       // Post creation timestamp
}
