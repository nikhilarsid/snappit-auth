package com.snapitt.backend_service.modules.profile.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.feed.repository.StoryFeedRepository;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.dto.request.UpdateProfileRequest;
import com.snapitt.backend_service.modules.profile.dto.response.ProfileResponse;
import com.snapitt.backend_service.modules.story.repository.StoryRepository;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService Unit Tests")
class ProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FollowRepository followRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private StoryFeedRepository storyFeedRepository;

    @InjectMocks
    private ProfileService profileService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .id("user-1").username("testuser")
                .profile(UserEntity.Profile.builder()
                        .name("Test User").bio("Hello!").avatarUrl("http://avatar.url").build())
                .followersCount(10L).followingCount(5L).postCount(3L)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfileTests {

        @Test
        @DisplayName("should return full profile for own profile view")
        void getProfile_ownProfile() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(eq("user-1"), any(Instant.class)))
                    .thenReturn(true);

            ProfileResponse response = profileService.getProfile("user-1", "testuser");

            assertThat(response.getUsername()).isEqualTo("testuser");
            assertThat(response.getName()).isEqualTo("Test User");
            assertThat(response.getBio()).isEqualTo("Hello!");
            assertThat(response.getFollowersCount()).isEqualTo(10L);
            assertThat(response.getHasStory()).isTrue();
        }

        @Test
        @DisplayName("should return profile with follow status for follower")
        void getProfile_asFollower() {
            FollowEntity follow = FollowEntity.builder()
                    .followerId("viewer-1").followingId("user-1").status(FollowStatus.approved).build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(followRepository.findByFollowerIdAndFollowingId("viewer-1", "user-1"))
                    .thenReturn(Optional.of(follow));
            when(storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(eq("user-1"), any(Instant.class)))
                    .thenReturn(false);

            ProfileResponse response = profileService.getProfile("viewer-1", "testuser");

            assertThat(response.getIsFollowing()).isTrue();
            assertThat(response.getFollowStatus()).isEqualTo("approved");
        }

        @Test
        @DisplayName("should return profile with pending follow status")
        void getProfile_pendingFollow() {
            FollowEntity pending = FollowEntity.builder()
                    .followerId("viewer-1").followingId("user-1").status(FollowStatus.pending).build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(followRepository.findByFollowerIdAndFollowingId("viewer-1", "user-1"))
                    .thenReturn(Optional.of(pending));

            ProfileResponse response = profileService.getProfile("viewer-1", "testuser");

            assertThat(response.getIsFollowing()).isFalse();
            assertThat(response.getFollowStatus()).isEqualTo("pending");
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND for nonexistent user")
        void getProfile_notFound() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profileService.getProfile("viewer", "nobody"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return profile with none follow status for stranger")
        void getProfile_stranger() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(followRepository.findByFollowerIdAndFollowingId("stranger", "user-1")).thenReturn(Optional.empty());

            ProfileResponse response = profileService.getProfile("stranger", "testuser");

            assertThat(response.getIsFollowing()).isFalse();
            assertThat(response.getFollowStatus()).isEqualTo("none");
        }
    }

    @Nested
    @DisplayName("getMyProfile")
    class GetMyProfileTests {

        @Test
        @DisplayName("should return own profile")
        void getMyProfile_success() {
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(eq("user-1"), any(Instant.class)))
                    .thenReturn(false);

            ProfileResponse response = profileService.getMyProfile("user-1");

            assertThat(response.getUsername()).isEqualTo("testuser");
            assertThat(response.getFollowersCount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND for missing user")
        void getMyProfile_notFound() {
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profileService.getMyProfile("nonexistent"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("should update name, bio, and avatar")
        void updateProfile_success() {
            UpdateProfileRequest update = new UpdateProfileRequest();
            update.setName("New Name");
            update.setBio("New Bio");
            update.setAvatarUrl("http://new-avatar.url");

            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(eq("user-1"), any(Instant.class)))
                    .thenReturn(false);

            ProfileResponse response = profileService.updateProfile("user-1", update);

            assertThat(response.getUsername()).isEqualTo("testuser");
            verify(userRepository).save(argThat(u -> 
                u.getProfile().getName().equals("New Name") &&
                u.getProfile().getBio().equals("New Bio") &&
                u.getProfile().getAvatarUrl().equals("http://new-avatar.url")
            ));
        }

        @Test
        @DisplayName("should update only name when only name provided")
        void updateProfile_onlyName() {
            UpdateProfileRequest update = new UpdateProfileRequest();
            update.setName("New Name");

            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(eq("user-1"), any(Instant.class)))
                    .thenReturn(false);

            profileService.updateProfile("user-1", update);

            verify(userRepository).save(argThat(u -> u.getProfile().getName().equals("New Name")));
        }

        @Test
        @DisplayName("should accept /uploads/ path for avatar")
        void updateProfile_uploadsPath() {
            UpdateProfileRequest update = new UpdateProfileRequest();
            update.setAvatarUrl("/uploads/avatar.jpg");

            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.existsByAuthorIdAndIsDeletedFalseAndExpiresAtGreaterThan(eq("user-1"), any(Instant.class)))
                    .thenReturn(false);

            profileService.updateProfile("user-1", update);

            verify(userRepository).save(argThat(u -> u.getProfile().getAvatarUrl().equals("/uploads/avatar.jpg")));
        }

        @Test
        @DisplayName("should throw NO_VALID_FIELDS when all fields blank")
        void updateProfile_noValidFields() {
            UpdateProfileRequest update = new UpdateProfileRequest();

            assertThatThrownBy(() -> profileService.updateProfile("user-1", update))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("NO_VALID_FIELDS"));
        }

        @Test
        @DisplayName("should throw INVALID_URL for non-http avatar URL")
        void updateProfile_invalidUrl() {
            UpdateProfileRequest update = new UpdateProfileRequest();
            update.setAvatarUrl("ftp://invalid.url");

            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> profileService.updateProfile("user-1", update))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("INVALID_URL"));
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user doesn't exist")
        void updateProfile_userNotFound() {
            UpdateProfileRequest update = new UpdateProfileRequest();
            update.setName("Name");

            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profileService.updateProfile("nonexistent", update))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getUserByUsername")
    class GetUserByUsernameTests {

        @Test
        @DisplayName("should return user when found")
        void getUserByUsername_found() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

            UserEntity result = profileService.getUserByUsername("testuser");

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should return null when not found")
        void getUserByUsername_notFound() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

            UserEntity result = profileService.getUserByUsername("nobody");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserByIdTests {

        @Test
        @DisplayName("should return user when found")
        void getUserById_found() {
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

            UserEntity result = profileService.getUserById("user-1");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when not found")
        void getUserById_notFound() {
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            UserEntity result = profileService.getUserById("nonexistent");

            assertThat(result).isNull();
        }
    }
}
