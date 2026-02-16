package com.snapitt.backend_service.modules.notification.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    public NotificationDto getNotificationById(String notificationId, String viewerId) {
        log.debug("Fetching notification by ID: {}", notificationId);

        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> {
                    log.warn("Notification not found: {}", notificationId);
                    return new AuthException("Notification not found", "NOT_FOUND", HttpStatus.NOT_FOUND);
                });

        UserEntity actor = userRepository.findById(notification.getActorId())
                .orElseThrow(() -> new AuthException("Actor not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

        return mapToDto(notification, actor, viewerId);
    }

    public PaginatedNotificationsResponse getNotifications(String userId, int limit, String cursor) {
        log.debug("Fetching notifications for user: {}, limit: {}, cursor: {}", userId, limit, cursor);

        Pageable pageable = PageRequest.of(0, limit + 1);  
        List<NotificationEntity> notifications;

        if (cursor == null) {
            
            notifications = notificationRepository.findByTargetUserIdOrderBySeenAscCreatedAtDesc(userId, pageable);
        } else {
            
            notifications = notificationRepository.findByTargetUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(
                    userId, cursor, pageable);
        }

        boolean hasMore = notifications.size() > limit;
        if (hasMore) {
            notifications = notifications.subList(0, limit);
        }

        List<NotificationDto> dtos = notifications.stream()
                .map(notification -> {
                    UserEntity actor = userRepository.findById(notification.getActorId())
                            .orElseThrow(() -> new AuthException("Actor not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
                    return mapToDto(notification, actor, userId);
                })
                .toList();

        String nextCursor = hasMore && !dtos.isEmpty() ? dtos.get(dtos.size() - 1).getId() : null;

        return PaginatedNotificationsResponse.builder()
                .data(dtos)
                .nextCursor(nextCursor)
                .build();
    }

    private NotificationDto mapToDto(NotificationEntity notification, UserEntity actor, String viewerId) {
        String avatarUrl = actor.getProfile() != null ? actor.getProfile().getAvatarUrl() : null;

        Boolean viewerFollowingActor = null;
        if (notification.getType() == com.snapitt.backend_service.modules.notification.model.NotificationType.FOLLOW
                || notification.getType() == com.snapitt.backend_service.modules.notification.model.NotificationType.FOLLOW_ACCEPTED) {
            viewerFollowingActor = followRepository.findByFollowerIdAndFollowingId(viewerId, actor.getId())
                    .map(f -> f.getStatus() == FollowStatus.approved)
                    .orElse(false);
        }

        return NotificationDto.builder()
                .id(notification.getId())
                .actorUsername(actor.getUsername())
                .actorAvatarUrl(avatarUrl)
                .type(notification.getType())
                .entityId(notification.getEntityId())
                .seen(notification.getSeen())
                .createdAt(notification.getCreatedAt())
                .viewerFollowingActor(viewerFollowingActor)
                .build();
    }
}
