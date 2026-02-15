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
 * LikeEntity - Represents a user's like on a post
 *
 * Schema:
 * {
 *   _id: ObjectId,
 *   userId: ObjectId,
 *   postId: ObjectId,
 *   createdAt: ISODate
 * }
 *
 * Indexes:
 * - { userId: 1, postId: 1 } - UNIQUE - Prevent duplicate likes from same user
 * - { postId: 1 }             - Fetch all likes for a post
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "likes")
@CompoundIndexes({
        @CompoundIndex(name = "user_post_unique_idx", def = "{ 'userId': 1, 'postId': 1 }", unique = true),
        @CompoundIndex(name = "post_idx", def = "{ 'postId': 1 }")
})
public class LikeEntity {
    @Id
    private String id;

    private String userId;          // User who liked the post
    private String postId;          // Post being liked
    private Instant createdAt;      // When the like was created
}
