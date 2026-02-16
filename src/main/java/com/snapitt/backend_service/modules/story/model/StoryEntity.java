package com.snapitt.backend_service.modules.story.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * StoryEntity - Represents a user-created story (ephemeral content)
 *
 * Schema:
 * {
 *   _id: ObjectId,
 *   authorId: ObjectId,
 *   mediaUrl: string,
 *   createdAt: ISODate,
 *   expiresAt: ISODate,
 *   isDeleted: boolean,
 *   deletedAt: ISODate
 * }
 *
 * Indexes:
 * - { expiresAt: 1 } with expireAfterSeconds: 0 (TTL index - auto-delete expired stories)
 * - { authorId: 1, createdAt: -1 }   // Fetch stories by user
 *
 * Note: Stories are soft-deleted (isDeleted flag) or auto-deleted by TTL index.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stories")
@CompoundIndexes({
        @CompoundIndex(name = "author_created_idx", def = "{ 'authorId': 1, 'createdAt': -1 }"),
})
public class StoryEntity {
    @Id
    private String id;

    private String authorId;        // User who created the story
    private String mediaUrl;        // URL to media (image/video)
    private Instant createdAt;      // Creation timestamp
    
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;      // When story expires (TTL index)
    
    private Boolean isDeleted;      // Soft delete flag
    private Instant deletedAt;      // When story was deleted
}
