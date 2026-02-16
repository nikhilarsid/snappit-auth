package com.snapitt.backend_service.modules.feed.model;

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
 * PostFeedEntity - Represents a post in a user's feed
 *
 * Schema:
 * {
 *   _id: ObjectId,
 *   userId: ObjectId,           // viewer/recipient
 *   postId: ObjectId,           // reference to post
 *   authorId: ObjectId,         // post creator (always a follower)
 *   createdAt: ISODate,         // copied from post.createdAt
 *   seen: boolean,              // read status
 *   insertedAt: ISODate         // when fanned out by background job
 * }
 *
 * Indexes:
 * - { userId: 1, seen: 1, createdAt: -1 }   // Primary query index (unseen first, then fresh)
 * - { userId: 1, postId: 1 } UNIQUE         // Prevent duplicates
 *
 * Note: Created and populated by background job listening to POST_CREATED events.
 * Background job ensures only approved followers' posts are added.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "post_feed")
@CompoundIndexes({
        @CompoundIndex(name = "user_seen_created_idx", def = "{ 'userId': 1, 'seen': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "user_post_unique_idx", def = "{ 'userId': 1, 'postId': 1 }", unique = true)
})
public class PostFeedEntity {
    @Id
    private String id;

    private String userId;          // Viewer/recipient of the feed
    private String postId;          // Reference to post collection
    private String authorId;        // Post creator (always an approved follower)
    private Instant createdAt;      // Post creation timestamp (copied from post)
    private Boolean seen;           // Whether user has seen this post
    private Instant insertedAt;     // When this feed record was created (fanout timestamp)
}
