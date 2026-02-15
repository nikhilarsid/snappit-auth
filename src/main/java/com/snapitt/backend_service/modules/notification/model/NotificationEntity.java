package com.snapitt.backend_service.modules.notification.model;

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
 * NotificationEntity - Represents a user notification
 *
 * Schema:
 * {
 *   _id: ObjectId,
 *   targetUserId: ObjectId,               // User who receives the notification
 *   actorId: ObjectId,                    // User who triggered the notification
 *   type: "LIKE" | "COMMENT" | "FOLLOW",  // Notification type
 *   entityId: ObjectId,                   // Post/Comment ID related to notification
 *   seen: boolean,                        // Whether user has seen this notification
 *   createdAt: ISODate                    // Creation timestamp
 * }
 *
 * Indexes:
 * - { targetUserId: 1, seen: 1, createdAt: -1 }   // Fetch notifications for user
 *   Query pattern: find({ targetUserId }).sort({ seen: 1, createdAt: -1 })
 *   Unseen notifications appear first, then sorted by newest first
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "target_seen_created_idx", def = "{ 'targetUserId': 1, 'seen': 1, 'createdAt': -1 }")
})
public class NotificationEntity {
    @Id
    private String id;

    private String targetUserId;           // User who receives the notification
    private String actorId;                // User who triggered the notification
    private NotificationType type;         // Type of notification
    private String entityId;               // Post/Comment ID
    private Boolean seen;                  // Read status
    private Instant createdAt;             // Creation timestamp
}
