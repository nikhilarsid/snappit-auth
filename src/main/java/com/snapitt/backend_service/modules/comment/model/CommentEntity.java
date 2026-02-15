package com.snapitt.backend_service.modules.comment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * CommentEntity - Represents a comment or reply on a post
 *
 * Schema:
 * {
 *   _id: ObjectId,
 *   postId: ObjectId,
 *   authorId: ObjectId,
 *   parentCommentId: ObjectId | null,      // For replies (null for top-level comments)
 *   text: string,
 *   tagged: [ObjectId],                    // List of tagged user IDs
 *   likeCount: number,                     // Managed by background job
 *   replyCount: number,                    // Count of replies to this comment
 *   createdAt: ISODate,
 *   isDeleted: boolean                     // Soft delete flag
 * }
 *
 * Indexes:
 * - { postId: 1, parentCommentId: 1, createdAt: -1 }  // Fetch comments/replies for a post
 * - { parentCommentId: 1, createdAt: -1 }            // Fetch replies to a comment
 *
 * Note: Soft delete (isDeleted flag). No hard deletion.
 * Like count managed by background job via COMMENT_LIKED events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "comments")
@CompoundIndexes({
        @CompoundIndex(name = "post_parent_created_idx", def = "{ 'postId': 1, 'parentCommentId': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "parent_created_idx", def = "{ 'parentCommentId': 1, 'createdAt': -1 }")
})
public class CommentEntity {
    @Id
    private String id;

    private String postId;              // Post this comment belongs to
    private String authorId;            // User who created the comment
    private String parentCommentId;     // For replies (null for top-level comments)
    private String text;                // Comment text
    private List<String> tagged;        // Tagged user IDs
    private Long likeCount;             // Number of likes (managed by background job)
    private Long replyCount;            // Count of replies to this comment
    private Instant createdAt;          // Creation timestamp
    private Boolean isDeleted;          // Soft delete flag
}
