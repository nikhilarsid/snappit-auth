package com.snapitt.backend_service.modules.feed.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedPostsResponse;
import com.snapitt.backend_service.modules.feed.dto.response.PaginatedFeedStoriesResponse;
import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import com.snapitt.backend_service.modules.feed.model.StoryFeedEntity;
import com.snapitt.backend_service.modules.feed.repository.PostFeedRepository;
import com.snapitt.backend_service.modules.feed.repository.StoryFeedRepository;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
import com.snapitt.backend_service.modules.post.service.PostService;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.story.dto.response.StoryResponse;
import com.snapitt.backend_service.modules.story.service.StoryService;
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
@DisplayName("FeedService Unit Tests")
class FeedServiceTest {

    @Mock private PostFeedRepository postFeedRepository;
    @Mock private StoryFeedRepository storyFeedRepository;
    @Mock private PostService postService;
    @Mock private StoryService storyService;
    @Mock private ProfileService profileService;

    @InjectMocks
    private FeedService feedService;

    private final String userId = "user-1";

    @Nested
    @DisplayName("getMyFeedPosts")
    class GetMyFeedPostsTests {

        @Test
        @DisplayName("should return feed posts with post details")
        void getMyFeedPosts_success() {
            PostFeedEntity feedRecord = PostFeedEntity.builder()
                    .id("feed-1").userId(userId).postId("post-1").seen(false).createdAt(Instant.now()).build();
            PostResponse postResponse = PostResponse.builder()
                    .id("post-1").authorUsername("author").mediaUrl("http://img.url").build();

            when(postFeedRepository.findByUserIdOrderBySeenAscCreatedAtDesc(eq(userId), any(PageRequest.class)))
                    .thenReturn(List.of(feedRecord));
            when(postService.getPostsByIds(List.of("post-1"), userId)).thenReturn(List.of(postResponse));

            PaginatedFeedPostsResponse response = feedService.getMyFeedPosts(userId, 20, null);

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getData().get(0).getPost().getId()).isEqualTo("post-1");
            assertThat(response.getNextCursor()).isEqualTo("feed-1");
        }

        @Test
        @DisplayName("should return empty response when no feed records")
        void getMyFeedPosts_empty() {
            when(postFeedRepository.findByUserIdOrderBySeenAscCreatedAtDesc(eq(userId), any(PageRequest.class)))
                    .thenReturn(List.of());

            PaginatedFeedPostsResponse response = feedService.getMyFeedPosts(userId, 20, null);

            assertThat(response.getData()).isEmpty();
            assertThat(response.getNextCursor()).isNull();
        }

        @Test
        @DisplayName("should use cursor for pagination")
        void getMyFeedPosts_withCursor() {
            when(postFeedRepository.findByUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(eq(userId), eq("cursor"), any(PageRequest.class)))
                    .thenReturn(List.of());

            PaginatedFeedPostsResponse response = feedService.getMyFeedPosts(userId, 20, "cursor");

            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("should filter out null posts when post deleted externally")
        void getMyFeedPosts_nullPostFiltered() {
            PostFeedEntity feedRecord = PostFeedEntity.builder()
                    .id("feed-1").userId(userId).postId("deleted-post").seen(false).createdAt(Instant.now()).build();

            when(postFeedRepository.findByUserIdOrderBySeenAscCreatedAtDesc(eq(userId), any(PageRequest.class)))
                    .thenReturn(List.of(feedRecord));
            when(postService.getPostsByIds(List.of("deleted-post"), userId)).thenReturn(List.of());

            PaginatedFeedPostsResponse response = feedService.getMyFeedPosts(userId, 20, null);

            assertThat(response.getData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMyFeedStories")
    class GetMyFeedStoriesTests {

        @Test
        @DisplayName("should return feed stories with creator info")
        void getMyFeedStories_success() {
            StoryFeedEntity feedRecord = StoryFeedEntity.builder()
                    .id("sfeed-1").userId(userId).creatorId("creator-1").seen(false)
                    .latestStoryAt(Instant.now()).isDeleted(false).build();
            UserEntity creator = UserEntity.builder()
                    .id("creator-1").username("creator")
                    .profile(UserEntity.Profile.builder().avatarUrl("http://avatar.url").build())
                    .build();
            StoryResponse latestStory = StoryResponse.builder()
                    .id("story-1").authorUsername("creator").mediaUrl("http://story.url").build();

            when(storyFeedRepository.findByUserIdAndIsDeletedFalseOrderBySeenAscLatestStoryAtDesc(eq(userId), any(PageRequest.class)))
                    .thenReturn(List.of(feedRecord));
            when(profileService.getUsersByIds(List.of("creator-1"))).thenReturn(List.of(creator));
            when(storyService.getLatestStoryByCreator("creator-1", userId)).thenReturn(latestStory);

            PaginatedFeedStoriesResponse response = feedService.getMyFeedStories(userId, 20, null);

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getData().get(0).getCreatorUsername()).isEqualTo("creator");
            assertThat(response.getData().get(0).getStoryId()).isEqualTo("story-1");
        }

        @Test
        @DisplayName("should filter out creators with no active stories")
        void getMyFeedStories_noActiveStory() {
            StoryFeedEntity feedRecord = StoryFeedEntity.builder()
                    .id("sfeed-1").userId(userId).creatorId("creator-1").seen(false)
                    .latestStoryAt(Instant.now()).isDeleted(false).build();
            UserEntity creator = UserEntity.builder()
                    .id("creator-1").username("creator")
                    .profile(UserEntity.Profile.builder().build()).build();

            when(storyFeedRepository.findByUserIdAndIsDeletedFalseOrderBySeenAscLatestStoryAtDesc(eq(userId), any(PageRequest.class)))
                    .thenReturn(List.of(feedRecord));
            when(profileService.getUsersByIds(List.of("creator-1"))).thenReturn(List.of(creator));
            when(storyService.getLatestStoryByCreator("creator-1", userId)).thenReturn(null);

            PaginatedFeedStoriesResponse response = feedService.getMyFeedStories(userId, 20, null);

            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("should return empty response when no feed records")
        void getMyFeedStories_empty() {
            when(storyFeedRepository.findByUserIdAndIsDeletedFalseOrderBySeenAscLatestStoryAtDesc(eq(userId), any(PageRequest.class)))
                    .thenReturn(List.of());

            PaginatedFeedStoriesResponse response = feedService.getMyFeedStories(userId, 20, null);

            assertThat(response.getData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("markPostAsRead")
    class MarkPostAsReadTests {

        @Test
        @DisplayName("should mark post feed record as seen")
        void markPostAsRead_success() {
            PostFeedEntity feedRecord = PostFeedEntity.builder()
                    .id("feed-1").userId(userId).postId("post-1").seen(false).build();

            when(postFeedRepository.findById("feed-1")).thenReturn(Optional.of(feedRecord));
            when(postFeedRepository.save(any(PostFeedEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            feedService.markPostAsRead("feed-1", userId);

            verify(postFeedRepository).save(argThat(PostFeedEntity::getSeen));
        }

        @Test
        @DisplayName("should throw POST_FEED_NOT_FOUND for missing record")
        void markPostAsRead_notFound() {
            when(postFeedRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> feedService.markPostAsRead("nonexistent", userId))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("POST_FEED_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN for wrong user")
        void markPostAsRead_forbidden() {
            PostFeedEntity feedRecord = PostFeedEntity.builder()
                    .id("feed-1").userId("other-user").postId("post-1").seen(false).build();

            when(postFeedRepository.findById("feed-1")).thenReturn(Optional.of(feedRecord));

            assertThatThrownBy(() -> feedService.markPostAsRead("feed-1", userId))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("markStoryAsRead")
    class MarkStoryAsReadTests {

        @Test
        @DisplayName("should mark story feed record as seen")
        void markStoryAsRead_success() {
            StoryFeedEntity feedRecord = StoryFeedEntity.builder()
                    .id("sfeed-1").userId(userId).creatorId("creator-1").seen(false).build();

            when(storyFeedRepository.findById("sfeed-1")).thenReturn(Optional.of(feedRecord));
            when(storyFeedRepository.save(any(StoryFeedEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            feedService.markStoryAsRead("sfeed-1", userId);

            verify(storyFeedRepository).save(argThat(StoryFeedEntity::getSeen));
        }

        @Test
        @DisplayName("should throw STORY_FEED_NOT_FOUND for missing record")
        void markStoryAsRead_notFound() {
            when(storyFeedRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> feedService.markStoryAsRead("nonexistent", userId))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("STORY_FEED_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("markStoryAsReadByCreator")
    class MarkStoryAsReadByCreatorTests {

        @Test
        @DisplayName("should mark story as seen by creator username")
        void markStoryAsReadByCreator_success() {
            UserEntity creator = UserEntity.builder().id("creator-1").username("creator").build();
            StoryFeedEntity feedRecord = StoryFeedEntity.builder()
                    .id("sfeed-1").userId(userId).creatorId("creator-1").seen(false).isDeleted(false).build();

            when(profileService.getUserByUsername("creator")).thenReturn(creator);
            when(storyFeedRepository.findByUserIdAndCreatorIdAndIsDeletedFalse(userId, "creator-1"))
                    .thenReturn(Optional.of(feedRecord));
            when(storyFeedRepository.save(any(StoryFeedEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            feedService.markStoryAsReadByCreator(userId, "creator");

            verify(storyFeedRepository).save(argThat(StoryFeedEntity::getSeen));
        }

        @Test
        @DisplayName("should silently return when creator not found")
        void markStoryAsReadByCreator_creatorNotFound() {
            when(profileService.getUserByUsername("nobody")).thenReturn(null);

            assertThatNoException().isThrownBy(() -> feedService.markStoryAsReadByCreator(userId, "nobody"));
            verify(storyFeedRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not update if already seen")
        void markStoryAsReadByCreator_alreadySeen() {
            UserEntity creator = UserEntity.builder().id("creator-1").username("creator").build();
            StoryFeedEntity feedRecord = StoryFeedEntity.builder()
                    .id("sfeed-1").userId(userId).creatorId("creator-1").seen(true).isDeleted(false).build();

            when(profileService.getUserByUsername("creator")).thenReturn(creator);
            when(storyFeedRepository.findByUserIdAndCreatorIdAndIsDeletedFalse(userId, "creator-1"))
                    .thenReturn(Optional.of(feedRecord));

            feedService.markStoryAsReadByCreator(userId, "creator");

            verify(storyFeedRepository, never()).save(any());
        }
    }
}
