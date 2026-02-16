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
 * StoryFeedEntity - Represents a story creator in a user's feed
 *
 * Schema:
 * {
 *   _id: ObjectId,
 *   userId: ObjectId,           // viewer/recipient
 *   creatorId: ObjectId,        // story creator (always a follower)
 *   latestStoryAt: ISODate,     // latest story timestamp from this creator
 *   seen: boolean,              // read status
 *   isDeleted: boolean          // soft delete flag
 * }
 *
 * Indexes:
 * - { userId: 1, seen: 1, latestStoryAt: -1 }   // Primary query index (unseen first, then latest)
 * - { userId: 1, creatorId: 1 } UNIQUE          // Prevent duplicates
 *
 * Note: One record per creator, updated when new story is created.
 * Created and maintained by background job listening to STORY_CREATED events.
 * Background job ensures only approved followers' stories are added.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "story_feed")
@CompoundIndexes({
        @CompoundIndex(name = "user_seen_latest_idx", def = "{ 'userId': 1, 'seen': 1, 'latestStoryAt': -1 }"),
        @CompoundIndex(name = "user_creator_unique_idx", def = "{ 'userId': 1, 'creatorId': 1 }", unique = true)
})
public class StoryFeedEntity {
    @Id
    private String id;

    private String userId;          // Viewer/recipient of the feed
    private String creatorId;       // Story creator (always an approved follower)
    private Instant latestStoryAt;  // Latest story timestamp from this creator
    private Boolean seen;           // Whether user has seen the latest story
    private Boolean isDeleted;      // Soft delete flag (when creator is unfollowed, etc)
}
