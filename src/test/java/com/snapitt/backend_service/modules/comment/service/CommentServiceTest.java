package com.snapitt.backend_service.modules.comment.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.comment.dto.request.CreateCommentRequest;
import com.snapitt.backend_service.modules.comment.dto.response.CommentResponse;
import com.snapitt.backend_service.modules.comment.dto.response.PaginatedCommentsResponse;
import com.snapitt.backend_service.modules.comment.model.CommentEntity;
import com.snapitt.backend_service.modules.comment.repository.CommentRepository;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
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
@DisplayName("CommentService Unit Tests")
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private FollowRepository followRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventService eventService;

    @InjectMocks
    private CommentService commentService;

    private UserEntity author;
    private PostEntity post;
    private CommentEntity comment;

    @BeforeEach
    void setUp() {
        author = UserEntity.builder()
                .id("author-1").username("author")
                .profile(UserEntity.Profile.builder().name("Author").avatarUrl("http://avatar.url").build())
                .build();

        post = PostEntity.builder()
                .id("post-1").authorId("author-1").mediaUrl("http://img.url")
                .likeCount(0L).commentCount(0L).createdAt(Instant.now())
                .build();

        comment = CommentEntity.builder()
                .id("comment-1").postId("post-1").authorId("author-1")
                .text("Great post!").likeCount(0L).replyCount(0L)
                .isDeleted(false).createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getComment")
    class GetCommentTests {

        @Test
        @DisplayName("should return comment for authorized viewer")
        void getComment_success() {
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

            CommentResponse response = commentService.getComment("comment-1", "author-1");

            assertThat(response.getId()).isEqualTo("comment-1");
            assertThat(response.getText()).isEqualTo("Great post!");
            assertThat(response.getAuthorUsername()).isEqualTo("author");
        }

        @Test
        @DisplayName("should throw COMMENT_NOT_FOUND for missing comment")
        void getComment_notFound() {
            when(commentRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.getComment("nonexistent", "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("COMMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("should throw COMMENT_NOT_FOUND for deleted comment")
        void getComment_deleted() {
            CommentEntity deleted = CommentEntity.builder().id("comment-2").postId("post-1").authorId("author-1")
                    .text("Deleted").isDeleted(true).build();
            when(commentRepository.findById("comment-2")).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> commentService.getComment("comment-2", "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("COMMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getCommentsByPost")
    class GetCommentsByPostTests {

        @Test
        @DisplayName("should return paginated comments")
        void getCommentsByPost_success() {
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(commentRepository.findByPostIdAndParentCommentIdNullAndIsDeletedFalseOrderByCreatedAtDesc(eq("post-1"), any(PageRequest.class)))
                    .thenReturn(List.of(comment));
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

            PaginatedCommentsResponse response = commentService.getCommentsByPost("post-1", 20, null, "author-1");

            assertThat(response.getData()).hasSize(1);
        }

        @Test
        @DisplayName("should throw POST_NOT_FOUND for missing post")
        void getCommentsByPost_postNotFound() {
            when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.getCommentsByPost("nonexistent", 20, null, "viewer"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("POST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("createComment")
    class CreateCommentTests {

        @Test
        @DisplayName("should create top-level comment")
        void createComment_success() {
            CreateCommentRequest req = new CreateCommentRequest("Nice!", null, null);

            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> {
                CommentEntity c = inv.getArgument(0);
                c.setId("comment-new");
                return c;
            });
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            CommentResponse response = commentService.createComment("post-1", "author-1", req);

            assertThat(response.getText()).isEqualTo("Nice!");
            verify(eventService).emitEvent(any(), eq("comment-new"), any());
        }

        @Test
        @DisplayName("should create reply and increment parent reply count")
        void createComment_reply() {
            CommentEntity parent = CommentEntity.builder().id("parent-1").postId("post-1").authorId("author-1")
                    .text("Parent").replyCount(0L).isDeleted(false).build();
            CreateCommentRequest req = new CreateCommentRequest("Reply!", "parent-1", null);

            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(commentRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> {
                CommentEntity c = inv.getArgument(0);
                if (c.getId() == null) c.setId("reply-1");
                return c;
            });
            when(userRepository.findById("author-1")).thenReturn(Optional.of(author));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            commentService.createComment("post-1", "author-1", req);

            verify(commentRepository, atLeast(2)).save(any(CommentEntity.class));
        }

        @Test
        @DisplayName("should throw VALIDATION_ERROR for blank text")
        void createComment_blankText() {
            CreateCommentRequest req = new CreateCommentRequest("", null, null);

            assertThatThrownBy(() -> commentService.createComment("post-1", "author-1", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should throw FORBIDDEN when not following post author")
        void createComment_forbidden() {
            CreateCommentRequest req = new CreateCommentRequest("Hi", null, null);

            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(followRepository.findByFollowerIdAndFollowingId("stranger", "author-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.createComment("post-1", "stranger", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }

        @Test
        @DisplayName("should throw COMMENT_NOT_FOUND for deleted parent comment")
        void createComment_deletedParent() {
            CommentEntity deletedParent = CommentEntity.builder().id("parent-1").postId("post-1")
                    .isDeleted(true).build();
            CreateCommentRequest req = new CreateCommentRequest("Reply", "parent-1", null);

            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(commentRepository.findById("parent-1")).thenReturn(Optional.of(deletedParent));

            assertThatThrownBy(() -> commentService.createComment("post-1", "author-1", req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("COMMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("deleteComment")
    class DeleteCommentTests {

        @Test
        @DisplayName("should soft-delete own comment")
        void deleteComment_asAuthor() {
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            commentService.deleteComment("comment-1", "author-1");

            verify(commentRepository).save(argThat(c -> c.getIsDeleted()));
        }

        @Test
        @DisplayName("should allow post author to delete any comment on their post")
        void deleteComment_asPostAuthor() {
            CommentEntity otherComment = CommentEntity.builder()
                    .id("comment-2").postId("post-1").authorId("other-user")
                    .text("Other comment").isDeleted(false).build();

            when(commentRepository.findById("comment-2")).thenReturn(Optional.of(otherComment));
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
            when(eventService.emitEvent(any(), any(), any())).thenReturn(null);

            commentService.deleteComment("comment-2", "author-1");

            verify(commentRepository).save(argThat(c -> c.getIsDeleted()));
        }

        @Test
        @DisplayName("should throw FORBIDDEN for unauthorized user")
        void deleteComment_forbidden() {
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));
            when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> commentService.deleteComment("comment-1", "stranger"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
        }

        @Test
        @DisplayName("should throw COMMENT_NOT_FOUND for already deleted comment")
        void deleteComment_alreadyDeleted() {
            CommentEntity deleted = CommentEntity.builder().id("c").postId("post-1").authorId("author-1")
                    .isDeleted(true).build();
            when(commentRepository.findById("c")).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> commentService.deleteComment("c", "author-1"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode()).isEqualTo("COMMENT_NOT_FOUND"));
        }
    }
}
