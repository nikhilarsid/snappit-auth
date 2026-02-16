package com.snapitt.backend_service.modules.post.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * PostResponse - DTO for a single post
 *
 * Used in:
 * - GET /api/v1/posts/{postId}
 * - POST /api/v1/posts (create response)
 * - GET /api/v1/posts/user/{username} (list items)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {
    private String id;              // Post ID
    private String authorUsername;  // Author's username
    private String mediaUrl;        // Media URL
    private String caption;         // Post caption
    private Long likeCount;         // Number of likes
    private Long commentCount;      // Number of comments
    private Boolean likedByViewer;  // Whether current viewer has liked this post
    private Instant createdAt;      // Creation timestamp
    private Boolean canDelete;      // True if viewer is the post author
}
