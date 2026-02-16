package com.snapitt.backend_service.modules.post.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * PostEntity - Represents a user-created post
 *
 * Schema:
 * {
 *   _id: ObjectId,
 *   authorId: ObjectId,
 *   mediaUrl: string,
 *   caption: string,
 *   likeCount: number,           // Updated by background job via POST_LIKED event
 *   commentCount: number,        // Updated by comment service
 *   createdAt: ISODate
 * }
 *
 * Indexes:
 * - { authorId: 1, createdAt: -1 }   // Fetch posts by user
 * - { createdAt: -1 }                // Fetch recent posts
 *
 * Note: Hard delete (no soft delete). Background job handles post_feed cleanup via POST_DELETED event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "posts")
@CompoundIndexes({
        @CompoundIndex(name = "author_created_idx", def = "{ 'authorId': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "created_idx", def = "{ 'createdAt': -1 }")
})
public class PostEntity {
    @Id
    private String id;

    private String authorId;        // User who created the post
    private String mediaUrl;        // URL to media (image/video)
    private String caption;         // Post description
    private Long likeCount;         // Number of likes
    private Long commentCount;      // Number of comments
    private Instant createdAt;      // Creation timestamp
}
