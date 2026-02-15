package com.snapitt.backend_service.modules.notification.repository;

import com.snapitt.backend_service.modules.notification.model.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * NotificationRepository - Data access for notifications
 *
 * Queries leverage index: { targetUserId: 1, seen: 1, createdAt: -1 }
 * Order priority: unseen first (seen: 1), then newest (createdAt: -1)
 */
public interface NotificationRepository extends MongoRepository<NotificationEntity, String> {

    /**
     * Find a notification by ID
     * @param id Notification ID
     * @return The notification if found
     */
    Optional<NotificationEntity> findById(String id);

    /**
     * Get paginated notifications for a user (sorted: unseen first, then fresh)
     * @param targetUserId User's ID who receives the notifications
     * @param pageable Pagination (first page for initial load)
     * @return Notifications sorted by seen (false first) then createdAt descending
     */
    List<NotificationEntity> findByTargetUserIdOrderBySeenAscCreatedAtDesc(String targetUserId, Pageable pageable);

    /**
     * Get paginated notifications with cursor (for subsequent pages)
     * @param targetUserId User's ID who receives the notifications
     * @param cursor Previous notification ID (cursor for pagination)
     * @param pageable Pagination (with limit)
     * @return Notifications after cursor, sorted by seen then createdAt descending
     */
    List<NotificationEntity> findByTargetUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(
            String targetUserId, String cursor, Pageable pageable);
}
