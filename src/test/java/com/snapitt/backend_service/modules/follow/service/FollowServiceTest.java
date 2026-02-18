package com.snapitt.backend_service.modules.follow.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowersResponse;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowingResponse;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
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
@DisplayName("FollowService Unit Tests")
class FollowServiceTest {

    @Mock private FollowRepository followRepository;
    @Mock private ProfileService profileService;
    @Mock private EventService eventService;

    @InjectMocks
    private FollowService followService;

    private UserEntity targetUser;
    private UserEntity followerUser;

    @BeforeEach
    void setUp() {
        targetUser = UserEntity.builder()
                .id("target-1").username("target")
                .profile(UserEntity.Profile.builder().name("Target").avatarUrl("http://avatar.url").build())
                .build();

        followerUser = UserEntity.builder()
                .id("follower-1").username("follower")
                .profile(UserEntity.Profile.builder().name("Follower").avatarUrl("http://follower-avatar.url").build())
                .build();
    }

    @Nested
    @DisplayName("createFollowRequest")
    class CreateFollowRequestTests {

        @Test
        @DisplayName("should create pending follow request and emit event")
        void createFollowRequest_success() {
            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.empty());
            when(followRepository.save(any(FollowEntity.class))).thenAnswer(inv -> {
                FollowEntity f = inv.getArgument(0);
                f.setId("follow-1");
                return f;
            });
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            followService.createFollowRequest("follower-1", "target");

            verify(followRepository).save(argThat(f -> f.getStatus() == FollowStatus.pending));
            verify(eventService).emitEvent(any(), eq("target-1"), any());
        }

        @Test
        @DisplayName("should re-send if previously rejected")
        void createFollowRequest_previouslyRejected() {
            FollowEntity rejected = FollowEntity.builder()
                    .id("follow-old").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.rejected).build();

            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(rejected));
            when(followRepository.save(any(FollowEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            followService.createFollowRequest("follower-1", "target");

            verify(followRepository).save(argThat(f -> f.getStatus() == FollowStatus.pending && f.getId().equals("follow-old")));
        }

        @Test
        @DisplayName("should throw CANNOT_FOLLOW_SELF for self-follow")
        void createFollowRequest_selfFollow() {
            UserEntity self = UserEntity.builder().id("follower-1").username("me").build();
            when(profileService.getUserByUsername("me")).thenReturn(self);

            assertThatThrownBy(() -> followService.createFollowRequest("follower-1", "me"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("CANNOT_FOLLOW_SELF"));
        }

        @Test
        @DisplayName("should throw ALREADY_FOLLOWING when already approved")
        void createFollowRequest_alreadyFollowing() {
            FollowEntity approved = FollowEntity.builder()
                    .followerId("follower-1").followingId("target-1").status(FollowStatus.approved).build();

            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> followService.createFollowRequest("follower-1", "target"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("ALREADY_FOLLOWING"));
        }

        @Test
        @DisplayName("should throw REQUEST_ALREADY_SENT when pending")
        void createFollowRequest_alreadyPending() {
            FollowEntity pending = FollowEntity.builder()
                    .followerId("follower-1").followingId("target-1").status(FollowStatus.pending).build();

            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> followService.createFollowRequest("follower-1", "target"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("REQUEST_ALREADY_SENT"));
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND for nonexistent target")
        void createFollowRequest_targetNotFound() {
            when(profileService.getUserByUsername("nobody")).thenReturn(null);

            assertThatThrownBy(() -> followService.createFollowRequest("follower-1", "nobody"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("approveFollow")
    class ApproveFollowTests {

        @Test
        @DisplayName("should approve pending follow request")
        void approveFollow_success() {
            FollowEntity pending = FollowEntity.builder()
                    .id("follow-1").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.pending).build();

            when(profileService.getUserByUsername("follower")).thenReturn(followerUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(pending));
            when(followRepository.save(any(FollowEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            followService.approveFollow("target-1", "follower");

            verify(followRepository).save(argThat(f -> f.getStatus() == FollowStatus.approved));
            verify(eventService).emitEvent(any(), eq("follow-1"), any());
        }

        @Test
        @DisplayName("should throw NO_PENDING_REQUEST when no pending request")
        void approveFollow_noPendingRequest() {
            when(profileService.getUserByUsername("follower")).thenReturn(followerUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.approveFollow("target-1", "follower"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("NO_PENDING_REQUEST"));
        }

        @Test
        @DisplayName("should throw NO_PENDING_REQUEST when request is already approved")
        void approveFollow_alreadyApproved() {
            FollowEntity approved = FollowEntity.builder()
                    .followerId("follower-1").followingId("target-1").status(FollowStatus.approved).build();

            when(profileService.getUserByUsername("follower")).thenReturn(followerUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> followService.approveFollow("target-1", "follower"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("NO_PENDING_REQUEST"));
        }
    }

    @Nested
    @DisplayName("rejectFollow")
    class RejectFollowTests {

        @Test
        @DisplayName("should reject pending follow request")
        void rejectFollow_success() {
            FollowEntity pending = FollowEntity.builder()
                    .id("follow-1").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.pending).build();

            when(profileService.getUserByUsername("follower")).thenReturn(followerUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(pending));
            when(followRepository.save(any(FollowEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            followService.rejectFollow("target-1", "follower");

            verify(followRepository).save(argThat(f -> f.getStatus() == FollowStatus.rejected));
        }

        @Test
        @DisplayName("should throw NO_PENDING_REQUEST when nothing to reject")
        void rejectFollow_noPending() {
            when(profileService.getUserByUsername("follower")).thenReturn(followerUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.rejectFollow("target-1", "follower"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("NO_PENDING_REQUEST"));
        }
    }

    @Nested
    @DisplayName("unfollow")
    class UnfollowTests {

        @Test
        @DisplayName("should unfollow approved relation and return UNFOLLOWED")
        void unfollow_approved() {
            FollowEntity approved = FollowEntity.builder()
                    .id("follow-1").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.approved).build();

            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(approved));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            String result = followService.unfollow("follower-1", "target");

            assertThat(result).isEqualTo("UNFOLLOWED");
            verify(followRepository).deleteByFollowerIdAndFollowingId("follower-1", "target-1");
        }

        @Test
        @DisplayName("should withdraw pending request and return FOLLOW_REQUEST_WITHDRAWN")
        void unfollow_pending() {
            FollowEntity pending = FollowEntity.builder()
                    .id("follow-1").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.pending).build();

            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.of(pending));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            String result = followService.unfollow("follower-1", "target");

            assertThat(result).isEqualTo("FOLLOW_REQUEST_WITHDRAWN");
            verify(followRepository).deleteByFollowerIdAndFollowingId("follower-1", "target-1");
        }

        @Test
        @DisplayName("should throw NOT_FOLLOWING when no relation")
        void unfollow_notFollowing() {
            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowerIdAndFollowingId("follower-1", "target-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.unfollow("follower-1", "target"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("NOT_FOLLOWING"));
        }

        @Test
        @DisplayName("should throw CANNOT_UNFOLLOW_SELF")
        void unfollow_self() {
            UserEntity self = UserEntity.builder().id("follower-1").username("self").build();
            when(profileService.getUserByUsername("self")).thenReturn(self);

            assertThatThrownBy(() -> followService.unfollow("follower-1", "self"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("CANNOT_UNFOLLOW_SELF"));
        }
    }

    @Nested
    @DisplayName("getFollowers")
    class GetFollowersTests {

        @Test
        @DisplayName("should return paginated followers list")
        void getFollowers_success() {
            FollowEntity follow = FollowEntity.builder()
                    .id("f1").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.approved).build();

            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowingIdAndStatusOrderByIdDesc(eq("target-1"), eq(FollowStatus.approved), any(PageRequest.class)))
                    .thenReturn(List.of(follow));
            when(profileService.getUsersByIds(List.of("follower-1"))).thenReturn(List.of(followerUser));
            when(followRepository.findByFollowerIdAndFollowingIdIn(anyString(), anyList())).thenReturn(List.of());

            PaginatedFollowersResponse response = followService.getFollowers("target", 20, null, "viewer-1");

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getData().get(0).getUsername()).isEqualTo("follower");
            assertThat(response.getNextCursor()).isEqualTo("f1");
        }

        @Test
        @DisplayName("should return empty data for user with no followers")
        void getFollowers_empty() {
            when(profileService.getUserByUsername("target")).thenReturn(targetUser);
            when(followRepository.findByFollowingIdAndStatusOrderByIdDesc(eq("target-1"), eq(FollowStatus.approved), any(PageRequest.class)))
                    .thenReturn(List.of());

            PaginatedFollowersResponse response = followService.getFollowers("target", 20, null, "viewer-1");

            assertThat(response.getData()).isEmpty();
            assertThat(response.getNextCursor()).isNull();
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND for nonexistent user")
        void getFollowers_userNotFound() {
            when(profileService.getUserByUsername("nobody")).thenReturn(null);

            assertThatThrownBy(() -> followService.getFollowers("nobody", 20, null, "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getFollowing")
    class GetFollowingTests {

        @Test
        @DisplayName("should return paginated following list")
        void getFollowing_success() {
            FollowEntity follow = FollowEntity.builder()
                    .id("f1").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.approved).build();

            when(profileService.getUserByUsername("follower")).thenReturn(followerUser);
            when(followRepository.findByFollowerIdAndStatusOrderByIdDesc(eq("follower-1"), eq(FollowStatus.approved), any(PageRequest.class)))
                    .thenReturn(List.of(follow));
            when(profileService.getUsersByIds(List.of("target-1"))).thenReturn(List.of(targetUser));
            when(followRepository.findByFollowerIdAndFollowingIdIn(anyString(), anyList())).thenReturn(List.of());

            PaginatedFollowingResponse response = followService.getFollowing("follower", 20, null, "viewer-1");

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getData().get(0).getUsername()).isEqualTo("target");
        }
    }

    @Nested
    @DisplayName("getPendingFollowRequests")
    class GetPendingTests {

        @Test
        @DisplayName("should return pending requests")
        void getPendingFollowRequests_success() {
            FollowEntity pending = FollowEntity.builder()
                    .id("f1").followerId("follower-1").followingId("target-1")
                    .status(FollowStatus.pending).build();

            when(followRepository.findByFollowingIdAndStatusOrderByIdDesc(eq("target-1"), eq(FollowStatus.pending), any(PageRequest.class)))
                    .thenReturn(List.of(pending));
            when(profileService.getUsersByIds(List.of("follower-1"))).thenReturn(List.of(followerUser));
            when(followRepository.findByFollowerIdAndFollowingIdIn(anyString(), anyList())).thenReturn(List.of());

            PaginatedFollowersResponse response = followService.getPendingFollowRequests("target-1", 20, null);

            assertThat(response.getData()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no pending requests")
        void getPendingFollowRequests_empty() {
            when(followRepository.findByFollowingIdAndStatusOrderByIdDesc(eq("target-1"), eq(FollowStatus.pending), any(PageRequest.class)))
                    .thenReturn(List.of());

            PaginatedFollowersResponse response = followService.getPendingFollowRequests("target-1", 20, null);

            assertThat(response.getData()).isEmpty();
        }
    }
}
