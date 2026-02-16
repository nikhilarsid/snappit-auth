package com.snapitt.backend_service.modules.comment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * CommentResponse - DTO for a single comment
 *
 * Used in:
 * - GET /api/v1/posts/{postId}/comments/{commentId}
 * - POST /api/v1/posts/{postId}/comments (create response)
 * - GET /api/v1/posts/{postId}/comments (list items)
 * - GET /api/v1/comments/{commentId}/replies (list items)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {
    private String id;                      // Comment ID
    private String postId;                  // Post ID
    private String authorUsername;          // Author's username
    private String text;                    // Comment text
    private List<String> tagged;            // Tagged user IDs
    private Long likeCount;                 // Number of likes
    private Long replyCount;                // Number of replies
    private Instant createdAt;              // Creation timestamp
    private String parentCommentId;         // Parent comment ID (if reply)
    private Boolean canDelete;              // True if viewer is author or post author
}
