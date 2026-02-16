package com.snapitt.backend_service.modules.notification.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.notification.dto.response.NotificationDto;
import com.snapitt.backend_service.modules.notification.dto.response.PaginatedNotificationsResponse;
import com.snapitt.backend_service.modules.notification.model.NotificationEntity;
import com.snapitt.backend_service.modules.notification.repository.NotificationRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * NotificationService - Business logic for notifications
 *
 * Responsibilities:
 * - Fetch notification by ID
 * - Fetch paginated notifications for user (cursor-based)
 * - Use user repository to fetch user details (like username)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Fetch a single notification by ID
     *
     * @param notificationId ID of the notification to fetch
     * @return NotificationDto with actor username populated
     * @throws AuthException (NOT_FOUND, 404) if notification not found
     */
    public NotificationDto getNotificationById(String notificationId) {
        log.debug("Fetching notification by ID: {}", notificationId);

        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> {
                    log.warn("Notification not found: {}", notificationId);
                    return new AuthException("Notification not found", "NOT_FOUND", HttpStatus.NOT_FOUND);
                });

        // Fetch actor's user to get username and avatar
        UserEntity actor = userRepository.findById(notification.getActorId())
                .orElseThrow(() -> new AuthException("Actor not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

        return mapToDto(notification, actor);
    }

    /**
     * Fetch paginated notifications for authenticated user
     *
     * Features:
     * - Cursor-based pagination
     * - Ordered by: unseen first, then newest
     * - Returns actor's username for each notification
     *
     * @param userId The authenticated user's ID
     * @param limit Items per page (1-50)
     * @param cursor Opaque pagination cursor (notification ID from previous page's last item)
     * @return PaginatedNotificationsResponse with notification list and nextCursor
     */
    public PaginatedNotificationsResponse getNotifications(String userId, int limit, String cursor) {
        log.debug("Fetching notifications for user: {}, limit: {}, cursor: {}", userId, limit, cursor);

        Pageable pageable = PageRequest.of(0, limit + 1);  // Fetch one extra to check for next page
        List<NotificationEntity> notifications;

        if (cursor == null) {
            // First page: fetch from beginning
            notifications = notificationRepository.findByTargetUserIdOrderBySeenAscCreatedAtDesc(userId, pageable);
        } else {
            // Subsequent pages: fetch after cursor
            notifications = notificationRepository.findByTargetUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(
                    userId, cursor, pageable);
        }

        // If we got more items than limit, we have a next page
        boolean hasMore = notifications.size() > limit;
        if (hasMore) {
            notifications = notifications.subList(0, limit);
        }

        // Fetch actor user entities for all notifications
        List<NotificationDto> dtos = notifications.stream()
                .map(notification -> {
                    UserEntity actor = userRepository.findById(notification.getActorId())
                            .orElseThrow(() -> new AuthException("Actor not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
                    return mapToDto(notification, actor);
                })
                .toList();

        String nextCursor = hasMore && !dtos.isEmpty() ? dtos.get(dtos.size() - 1).getId() : null;

        return PaginatedNotificationsResponse.builder()
                .data(dtos)
                .nextCursor(nextCursor)
                .build();
    }

    /**
     * Map NotificationEntity to NotificationDto
     *
     * @param notification The notification entity
     * @param actorUsername Username of the actor who triggered the notification
     * @return Mapped DTO
     */
    private NotificationDto mapToDto(NotificationEntity notification, UserEntity actor) {
        String avatarUrl = actor.getProfile() != null ? actor.getProfile().getAvatarUrl() : null;
        return NotificationDto.builder()
                .id(notification.getId())
                .actorUsername(actor.getUsername())
                .actorAvatarUrl(avatarUrl)
                .type(notification.getType())
                .entityId(notification.getEntityId())
                .seen(notification.getSeen())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
