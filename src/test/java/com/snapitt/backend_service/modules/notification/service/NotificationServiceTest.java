package com.snapitt.backend_service.modules.notification.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.notification.dto.response.NotificationDto;
import com.snapitt.backend_service.modules.notification.dto.response.PaginatedNotificationsResponse;
import com.snapitt.backend_service.modules.notification.model.NotificationEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationType;
import com.snapitt.backend_service.modules.notification.repository.NotificationRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FollowRepository followRepository;

    @InjectMocks
    private NotificationService notificationService;

    private UserEntity actor;
    private NotificationEntity notification;

    @BeforeEach
    void setUp() {
        actor = UserEntity.builder()
                .id("actor-1").username("actor")
                .profile(UserEntity.Profile.builder().name("Actor").avatarUrl("http://avatar.url").build())
                .build();

        notification = NotificationEntity.builder()
                .id("notif-1").targetUserId("user-1").actorId("actor-1")
                .type(NotificationType.LIKE).entityId("post-1")
                .seen(false).createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getNotificationById")
    class GetNotificationByIdTests {

        @Test
        @DisplayName("should return notification with actor details")
        void getNotificationById_success() {
            when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));
            when(userRepository.findById("actor-1")).thenReturn(Optional.of(actor));

            NotificationDto dto = notificationService.getNotificationById("notif-1", "user-1");

            assertThat(dto.getId()).isEqualTo("notif-1");
            assertThat(dto.getActorUsername()).isEqualTo("actor");
            assertThat(dto.getActorAvatarUrl()).isEqualTo("http://avatar.url");
            assertThat(dto.getType()).isEqualTo(NotificationType.LIKE);
        }

        @Test
        @DisplayName("should include follow status for FOLLOW type notifications")
        void getNotificationById_followType() {
            NotificationEntity followNotif = NotificationEntity.builder()
                    .id("notif-2").targetUserId("user-1").actorId("actor-1")
                    .type(NotificationType.FOLLOW).entityId("rel-1")
                    .seen(false).createdAt(Instant.now()).build();
            FollowEntity follow = FollowEntity.builder()
                    .followerId("user-1").followingId("actor-1").status(FollowStatus.approved).build();

            when(notificationRepository.findById("notif-2")).thenReturn(Optional.of(followNotif));
            when(userRepository.findById("actor-1")).thenReturn(Optional.of(actor));
            when(followRepository.findByFollowerIdAndFollowingId("user-1", "actor-1")).thenReturn(Optional.of(follow));

            NotificationDto dto = notificationService.getNotificationById("notif-2", "user-1");

            assertThat(dto.getViewerFollowingActor()).isTrue();
            assertThat(dto.getViewerFollowStatus()).isEqualTo("approved");
        }

        @Test
        @DisplayName("should throw NOT_FOUND for missing notification")
        void getNotificationById_notFound() {
            when(notificationRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getNotificationById("nonexistent", "user-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getNotifications")
    class GetNotificationsTests {

        @Test
        @DisplayName("should return paginated notifications")
        void getNotifications_success() {
            when(notificationRepository.findByTargetUserIdOrderBySeenAscCreatedAtDesc(eq("user-1"), any()))
                    .thenReturn(List.of(notification));
            when(userRepository.findById("actor-1")).thenReturn(Optional.of(actor));

            PaginatedNotificationsResponse response = notificationService.getNotifications("user-1", 20, null);

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getNextCursor()).isNull();
        }

        @Test
        @DisplayName("should set nextCursor when hasMore is true")
        void getNotifications_hasMore() {
            // Return limit + 1 items to trigger hasMore
            NotificationEntity notif2 = NotificationEntity.builder()
                    .id("notif-2").targetUserId("user-1").actorId("actor-1")
                    .type(NotificationType.COMMENT).entityId("c-1")
                    .seen(false).createdAt(Instant.now()).build();

            when(notificationRepository.findByTargetUserIdOrderBySeenAscCreatedAtDesc(eq("user-1"), any()))
                    .thenReturn(List.of(notification, notif2));
            when(userRepository.findById("actor-1")).thenReturn(Optional.of(actor));

            PaginatedNotificationsResponse response = notificationService.getNotifications("user-1", 1, null);

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getNextCursor()).isEqualTo("notif-1");
        }

        @Test
        @DisplayName("should return empty list when no notifications")
        void getNotifications_empty() {
            when(notificationRepository.findByTargetUserIdOrderBySeenAscCreatedAtDesc(eq("user-1"), any()))
                    .thenReturn(List.of());

            PaginatedNotificationsResponse response = notificationService.getNotifications("user-1", 20, null);

            assertThat(response.getData()).isEmpty();
            assertThat(response.getNextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("getUnseenCount")
    class GetUnseenCountTests {

        @Test
        @DisplayName("should return unseen count")
        void getUnseenCount_success() {
            when(notificationRepository.countByTargetUserIdAndSeen("user-1", false)).thenReturn(5L);

            long count = notificationService.getUnseenCount("user-1");

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("should return 0 when all seen")
        void getUnseenCount_zero() {
            when(notificationRepository.countByTargetUserIdAndSeen("user-1", false)).thenReturn(0L);

            long count = notificationService.getUnseenCount("user-1");

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("markAllAsSeen")
    class MarkAllAsSeenTests {

        @Test
        @DisplayName("should mark all unseen notifications as seen")
        void markAllAsSeen_success() {
            NotificationEntity unseen1 = NotificationEntity.builder()
                    .id("n1").targetUserId("user-1").seen(false).build();
            NotificationEntity unseen2 = NotificationEntity.builder()
                    .id("n2").targetUserId("user-1").seen(false).build();

            when(notificationRepository.findByTargetUserIdAndSeen("user-1", false))
                    .thenReturn(List.of(unseen1, unseen2));

            notificationService.markAllAsSeen("user-1");

            verify(notificationRepository).saveAll(argThat(list -> {
                List<NotificationEntity> items = (List<NotificationEntity>) list;
                return items.stream().allMatch(NotificationEntity::getSeen);
            }));
        }

        @Test
        @DisplayName("should do nothing when no unseen notifications")
        void markAllAsSeen_noneUnseen() {
            when(notificationRepository.findByTargetUserIdAndSeen("user-1", false))
                    .thenReturn(List.of());

            notificationService.markAllAsSeen("user-1");

            verify(notificationRepository, never()).saveAll(any());
        }
    }
}
