package com.snapitt.backend_service.modules.story.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.story.dto.request.CreateStoryRequest;
import com.snapitt.backend_service.modules.story.dto.response.PaginatedStoriesResponse;
import com.snapitt.backend_service.modules.story.dto.response.StoryResponse;
import com.snapitt.backend_service.modules.story.model.StoryEntity;
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
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoryService Unit Tests")
class StoryServiceTest {

    @Mock private StoryRepository storyRepository;
    @Mock private FollowRepository followRepository;
    @Mock private ProfileService profileService;
    @Mock private EventService eventService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private StoryService storyService;

    private UserEntity author;
    private StoryEntity story;

    @BeforeEach
    void setUp() {
        author = UserEntity.builder()
                .id("author-1").username("author")
                .profile(UserEntity.Profile.builder().name("Author").avatarUrl("http://avatar.url").build())
                .build();

        story = StoryEntity.builder()
                .id("story-1").authorId("author-1").mediaUrl("http://story.url")
                .createdAt(Instant.now()).expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .isDeleted(false)
                .build();
    }

    @Nested
    @DisplayName("getStory")
    class GetStoryTests {

        @Test
        @DisplayName("should return story for author")
        void getStory_asAuthor() {
            when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

            StoryResponse response = storyService.getStory("story-1", "author-1");

            assertThat(response.getId()).isEqualTo("story-1");
            assertThat(response.getAuthorUsername()).isEqualTo("author");
            assertThat(response.getCanDelete()).isTrue();
        }

        @Test
        @DisplayName("should return story for approved follower")
        void getStory_asFollower() {
            FollowEntity follow = FollowEntity.builder()
                    .followerId("viewer-1").followingId("author-1").status(FollowStatus.approved).build();

            when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));
            when(followRepository.findByFollowerIdAndFollowingId("viewer-1", "author-1")).thenReturn(Optional.of(follow));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

            StoryResponse response = storyService.getStory("story-1", "viewer-1");

            assertThat(response.getCanDelete()).isFalse();
        }

        @Test
        @DisplayName("should throw STORY_NOT_FOUND for missing story")
        void getStory_notFound() {
            when(storyRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> storyService.getStory("nonexistent", "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("STORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw STORY_NOT_FOUND for deleted story")
        void getStory_deleted() {
            StoryEntity deleted = StoryEntity.builder().id("s").authorId("a").isDeleted(true).build();
            when(storyRepository.findById("s")).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> storyService.getStory("s", "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("STORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN for non-follower")
        void getStory_forbidden() {
            when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));
            when(followRepository.findByFollowerIdAndFollowingId("stranger", "author-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> storyService.getStory("story-1", "stranger"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("getStoriesByUsername")
    class GetStoriesByUsernameTests {

        @Test
        @DisplayName("should return paginated stories")
        void getStoriesByUsername_success() {
            when(profileService.getUserByUsername("author")).thenReturn(author);
            when(storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("author-1"), any(PageRequest.class)))
                    .thenReturn(List.of(story));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

            PaginatedStoriesResponse response = storyService.getStoriesByUsername("author", 20, null, "author-1");

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getNextCursor()).isEqualTo("story-1");
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND for nonexistent user")
        void getStoriesByUsername_userNotFound() {
            when(profileService.getUserByUsername("nobody")).thenReturn(null);

            assertThatThrownBy(() -> storyService.getStoriesByUsername("nobody", 20, null, "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("createStory")
    class CreateStoryTests {

        @Test
        @DisplayName("should create story and emit event")
        void createStory_success() {
            CreateStoryRequest req = new CreateStoryRequest("http://media.url", Instant.now().plus(24, ChronoUnit.HOURS));

            when(storyRepository.save(any(StoryEntity.class))).thenAnswer(inv -> {
                StoryEntity s = inv.getArgument(0);
                s.setId("story-new");
                return s;
            });
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            StoryResponse response = storyService.createStory("author-1", req);

            assertThat(response.getMediaUrl()).isEqualTo("http://media.url");
            assertThat(response.getCanDelete()).isTrue();
            verify(eventService).emitEvent(any(), eq("story-new"), any());
        }

        @Test
        @DisplayName("should throw VALIDATION_ERROR for blank media URL")
        void createStory_blankMedia() {
            CreateStoryRequest req = new CreateStoryRequest("", Instant.now().plus(24, ChronoUnit.HOURS));

            assertThatThrownBy(() -> storyService.createStory("author-1", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should throw VALIDATION_ERROR for past expiration")
        void createStory_pastExpiration() {
            CreateStoryRequest req = new CreateStoryRequest("http://media.url", Instant.now().minus(1, ChronoUnit.HOURS));

            assertThatThrownBy(() -> storyService.createStory("author-1", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should throw VALIDATION_ERROR for null expiration")
        void createStory_nullExpiration() {
            CreateStoryRequest req = new CreateStoryRequest("http://media.url", null);

            assertThatThrownBy(() -> storyService.createStory("author-1", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("deleteStory")
    class DeleteStoryTests {

        @Test
        @DisplayName("should soft-delete story and emit event")
        void deleteStory_success() {
            when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));
            when(storyRepository.save(any(StoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            storyService.deleteStory("story-1", "author-1");

            verify(storyRepository).save(argThat(s -> s.getIsDeleted()));
            verify(eventService).emitEvent(any(), eq("story-1"), any());
        }

        @Test
        @DisplayName("should throw STORY_NOT_FOUND for missing story")
        void deleteStory_notFound() {
            when(storyRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> storyService.deleteStory("nonexistent", "author-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("STORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN for non-author")
        void deleteStory_notAuthor() {
            when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));

            assertThatThrownBy(() -> storyService.deleteStory("story-1", "other-user"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }

        @Test
        @DisplayName("should throw STORY_NOT_FOUND for already deleted story")
        void deleteStory_alreadyDeleted() {
            StoryEntity deleted = StoryEntity.builder().id("story-1").authorId("author-1").isDeleted(true).build();
            when(storyRepository.findById("story-1")).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> storyService.deleteStory("story-1", "author-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("STORY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getMyStory")
    class GetMyStoryTests {

        @Test
        @DisplayName("should return own story")
        void getMyStory_success() {
            when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

            StoryResponse response = storyService.getMyStory("story-1", "author-1");

            assertThat(response.getId()).isEqualTo("story-1");
            assertThat(response.getCanDelete()).isTrue();
        }

        @Test
        @DisplayName("should throw FORBIDDEN for non-owner")
        void getMyStory_notOwner() {
            when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));

            assertThatThrownBy(() -> storyService.getMyStory("story-1", "other-user"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("getLatestStoryByCreator")
    class GetLatestStoryByCreatorTests {

        @Test
        @DisplayName("should return latest non-expired story")
        void getLatestStoryByCreator_success() {
            when(storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("author-1"), any(PageRequest.class)))
                    .thenReturn(List.of(story));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

            StoryResponse response = storyService.getLatestStoryByCreator("author-1", "author-1");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("story-1");
        }

        @Test
        @DisplayName("should return null when no stories exist")
        void getLatestStoryByCreator_noStories() {
            when(storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("author-1"), any(PageRequest.class)))
                    .thenReturn(List.of());

            StoryResponse response = storyService.getLatestStoryByCreator("author-1", "author-1");

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("should return null when latest story is expired")
        void getLatestStoryByCreator_expired() {
            StoryEntity expired = StoryEntity.builder()
                    .id("s1").authorId("author-1").mediaUrl("http://url")
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .isDeleted(false).build();

            when(storyRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("author-1"), any(PageRequest.class)))
                    .thenReturn(List.of(expired));

            StoryResponse response = storyService.getLatestStoryByCreator("author-1", "author-1");

            assertThat(response).isNull();
        }
    }
}
