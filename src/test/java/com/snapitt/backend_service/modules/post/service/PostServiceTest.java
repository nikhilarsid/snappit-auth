package com.snapitt.backend_service.modules.post.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.post.dto.request.CreatePostRequest;
import com.snapitt.backend_service.modules.post.dto.response.PaginatedPostsResponse;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
import com.snapitt.backend_service.modules.post.model.LikeEntity;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.LikeRepository;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
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
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostService Unit Tests")
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private FollowRepository followRepository;
    @Mock private ProfileService profileService;
    @Mock private EventService eventService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    private UserEntity author;
    private PostEntity post;

    @BeforeEach
    void setUp() {
        author = UserEntity.builder()
                .id("author-1").username("author")
                .profile(UserEntity.Profile.builder().name("Author").avatarUrl("http://avatar.url").build())
                .build();

        post = PostEntity.builder()
                .id("post-1").authorId("author-1").mediaUrl("http://img.url")
                .caption("Test caption").likeCount(5L).commentCount(2L)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getPost")
    class GetPostTests {

        @Test
        @DisplayName("should return post for post author")
        void getPost_asAuthor() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(likeRepository.findByUserIdAndPostId("author-1", "post-1")).thenReturn(Optional.empty());

            PostResponse response = postService.getPost("post-1", "author-1");

            assertThat(response.getId()).isEqualTo("post-1");
            assertThat(response.getAuthorUsername()).isEqualTo("author");
            assertThat(response.getCanDelete()).isTrue();
        }

        @Test
        @DisplayName("should return post for approved follower")
        void getPost_asFollower() {
            FollowEntity follow = FollowEntity.builder().followerId("viewer-1").followingId("author-1").status(FollowStatus.approved).build();
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(followRepository.findByFollowerIdAndFollowingId("viewer-1", "author-1")).thenReturn(Optional.of(follow));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(likeRepository.findByUserIdAndPostId("viewer-1", "post-1")).thenReturn(Optional.empty());

            PostResponse response = postService.getPost("post-1", "viewer-1");

            assertThat(response.getCanDelete()).isFalse();
            assertThat(response.getLikedByViewer()).isFalse();
        }

        @Test
        @DisplayName("should throw POST_NOT_FOUND when post doesn't exist")
        void getPost_notFound() {
            when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getPost("nonexistent", "viewer-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("POST_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN for non-follower")
        void getPost_forbidden() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(followRepository.findByFollowerIdAndFollowingId("stranger", "author-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getPost("post-1", "stranger"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN for null viewer")
        void getPost_nullViewer() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.getPost("post-1", null))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("getPostsByUsername")
    class GetPostsByUsernameTests {

        @Test
        @DisplayName("should return paginated posts for own profile")
        void getPostsByUsername_ownProfile() {
            when(profileService.getUserByUsername("author")).thenReturn(author);
            when(postRepository.findByAuthorIdOrderByCreatedAtDesc(eq("author-1"), any(PageRequest.class)))
                    .thenReturn(List.of(post));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(likeRepository.findByUserIdAndPostId("author-1", "post-1")).thenReturn(Optional.empty());

            PaginatedPostsResponse response = postService.getPostsByUsername("author", 20, null, "author-1");

            assertThat(response.getData()).hasSize(1);
            assertThat(response.getNextCursor()).isEqualTo("post-1");
        }

        @Test
        @DisplayName("should return empty list when no posts")
        void getPostsByUsername_empty() {
            when(profileService.getUserByUsername("author")).thenReturn(author);
            when(postRepository.findByAuthorIdOrderByCreatedAtDesc(eq("author-1"), any(PageRequest.class)))
                    .thenReturn(List.of());

            PaginatedPostsResponse response = postService.getPostsByUsername("author", 20, null, "author-1");

            assertThat(response.getData()).isEmpty();
            assertThat(response.getNextCursor()).isNull();
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when username doesn't exist")
        void getPostsByUsername_userNotFound() {
            when(profileService.getUserByUsername("nobody")).thenReturn(null);

            assertThatThrownBy(() -> postService.getPostsByUsername("nobody", 20, null, "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("should use cursor for pagination")
        void getPostsByUsername_withCursor() {
            when(profileService.getUserByUsername("author")).thenReturn(author);
            when(postRepository.findByAuthorIdAndIdLessThanOrderByCreatedAtDesc(eq("author-1"), eq("cursor-1"), any(PageRequest.class)))
                    .thenReturn(List.of(post));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(likeRepository.findByUserIdAndPostId("author-1", "post-1")).thenReturn(Optional.empty());

            PaginatedPostsResponse response = postService.getPostsByUsername("author", 20, "cursor-1", "author-1");

            assertThat(response.getData()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("createPost")
    class CreatePostTests {

        @Test
        @DisplayName("should create post and emit event")
        void createPost_success() {
            CreatePostRequest req = new CreatePostRequest("http://img.url", "My caption");
            PostEntity saved = PostEntity.builder()
                    .id("post-new").authorId("author-1").mediaUrl("http://img.url")
                    .caption("My caption").likeCount(0L).commentCount(0L).createdAt(Instant.now())
                    .build();

            when(postRepository.save(any(PostEntity.class))).thenReturn(saved);
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(likeRepository.findByUserIdAndPostId("author-1", "post-new")).thenReturn(Optional.empty());
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            PostResponse response = postService.createPost("author-1", req);

            assertThat(response.getMediaUrl()).isEqualTo("http://img.url");
            assertThat(response.getCaption()).isEqualTo("My caption");
            assertThat(response.getCanDelete()).isTrue();
            verify(eventService).emitEvent(any(), eq("post-new"), any());
        }

        @Test
        @DisplayName("should throw VALIDATION_ERROR for blank media URL")
        void createPost_blankMediaUrl() {
            CreatePostRequest req = new CreatePostRequest("", "caption");

            assertThatThrownBy(() -> postService.createPost("author-1", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should throw VALIDATION_ERROR for null media URL")
        void createPost_nullMediaUrl() {
            CreatePostRequest req = new CreatePostRequest(null, "caption");

            assertThatThrownBy(() -> postService.createPost("author-1", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("deletePost")
    class DeletePostTests {

        @Test
        @DisplayName("should delete post and emit event")
        void deletePost_success() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            postService.deletePost("post-1", "author-1");

            verify(postRepository).deleteById("post-1");
            verify(eventService).emitEvent(any(), eq("post-1"), any());
        }

        @Test
        @DisplayName("should throw POST_NOT_FOUND when post doesn't exist")
        void deletePost_notFound() {
            when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.deletePost("nonexistent", "author-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("POST_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN when not the author")
        void deletePost_notAuthor() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.deletePost("post-1", "other-user"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("likePost")
    class LikePostTests {

        @Test
        @DisplayName("should like post and emit event")
        void likePost_success() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(likeRepository.findByUserIdAndPostId("viewer-1", "post-1")).thenReturn(Optional.empty());
            when(likeRepository.save(any(LikeEntity.class))).thenReturn(LikeEntity.builder().build());
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            postService.likePost("post-1", "viewer-1");

            verify(likeRepository).save(any(LikeEntity.class));
            verify(eventService).emitEvent(any(), eq("post-1"), any());
        }

        @Test
        @DisplayName("should throw ALREADY_LIKED when already liked")
        void likePost_alreadyLiked() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(likeRepository.findByUserIdAndPostId("viewer-1", "post-1"))
                    .thenReturn(Optional.of(LikeEntity.builder().build()));

            assertThatThrownBy(() -> postService.likePost("post-1", "viewer-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("ALREADY_LIKED"));
        }

        @Test
        @DisplayName("should throw POST_NOT_FOUND when post doesn't exist")
        void likePost_postNotFound() {
            when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.likePost("nonexistent", "viewer-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("POST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("unlikePost")
    class UnlikePostTests {

        @Test
        @DisplayName("should unlike post and emit event")
        void unlikePost_success() {
            LikeEntity like = LikeEntity.builder().id("like-1").userId("viewer-1").postId("post-1").build();
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(likeRepository.findByUserIdAndPostId("viewer-1", "post-1")).thenReturn(Optional.of(like));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            postService.unlikePost("post-1", "viewer-1");

            verify(likeRepository).deleteByUserIdAndPostId("viewer-1", "post-1");
            verify(eventService).emitEvent(any(), eq("post-1"), any());
        }

        @Test
        @DisplayName("should throw NOT_LIKED when not liked yet")
        void unlikePost_notLiked() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(likeRepository.findByUserIdAndPostId("viewer-1", "post-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.unlikePost("post-1", "viewer-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("NOT_LIKED"));
        }
    }

    @Nested
    @DisplayName("getMyPost")
    class GetMyPostTests {

        @Test
        @DisplayName("should return own post")
        void getMyPost_success() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(likeRepository.findByUserIdAndPostId("author-1", "post-1")).thenReturn(Optional.empty());

            PostResponse response = postService.getMyPost("post-1", "author-1");

            assertThat(response.getId()).isEqualTo("post-1");
            assertThat(response.getCanDelete()).isTrue();
        }

        @Test
        @DisplayName("should throw FORBIDDEN for non-owner")
        void getMyPost_notOwner() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.getMyPost("post-1", "other-user"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("getPostsByIds")
    class GetPostsByIdsTests {

        @Test
        @DisplayName("should return posts in order of provided IDs")
        void getPostsByIds_success() {
            PostEntity post2 = PostEntity.builder().id("post-2").authorId("author-1").mediaUrl("http://img2.url").likeCount(0L).commentCount(0L).createdAt(Instant.now()).build();
            when(postRepository.findAllById(List.of("post-1", "post-2"))).thenReturn(List.of(post, post2));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(likeRepository.findByUserIdAndPostId(anyString(), anyString())).thenReturn(Optional.empty());

            List<PostResponse> result = postService.getPostsByIds(List.of("post-1", "post-2"), "author-1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("post-1");
        }

        @Test
        @DisplayName("should return empty list for null IDs")
        void getPostsByIds_null() {
            List<PostResponse> result = postService.getPostsByIds(null, "viewer");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty IDs")
        void getPostsByIds_empty() {
            List<PostResponse> result = postService.getPostsByIds(List.of(), "viewer");
            assertThat(result).isEmpty();
        }
    }
}
