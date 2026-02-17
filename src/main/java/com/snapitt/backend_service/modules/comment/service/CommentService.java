package com.snapitt.backend_service.modules.comment.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.comment.dto.request.CreateCommentRequest;
import com.snapitt.backend_service.modules.comment.dto.response.CommentResponse;
import com.snapitt.backend_service.modules.comment.dto.response.PaginatedCommentsResponse;
import com.snapitt.backend_service.modules.comment.model.CommentEntity;
import com.snapitt.backend_service.modules.comment.repository.CommentRepository;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final EventService eventService;

    public CommentResponse getComment(String commentId, String viewerId) {
        try {
            CommentEntity comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (comment.getIsDeleted() != null && comment.getIsDeleted()) {
                throw new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            PostEntity post = postRepository.findById(comment.getPostId())
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!hasAccessToPost(post.getAuthorId(), viewerId)) {
                throw new AuthException("You don't have access to view this comment", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            return mapToResponse(comment, postRepository.findById(comment.getPostId()).orElse(null), viewerId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving comment: {}", commentId, ex);
            throw new AuthException("An error occurred while retrieving comment", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedCommentsResponse getCommentsByPost(String postId, int limit, String cursor, String viewerId) {
        try {
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!hasAccessToPost(post.getAuthorId(), viewerId)) {
                throw new AuthException("You don't have access to view these comments", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

            List<CommentEntity> comments;
            if (cursor == null) {
                comments = commentRepository.findByPostIdAndParentCommentIdNullAndIsDeletedFalseOrderByCreatedAtDesc(postId, pageRequest);
            } else {
                comments = commentRepository.findByPostIdAndParentCommentIdNullAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(postId, cursor, pageRequest);
            }

            boolean hasMore = comments.size() > pageSize;
            if (hasMore) {
                comments = comments.subList(0, pageSize);
            }

            List<CommentResponse> data = comments.stream()
                    .map(comment -> mapToResponse(comment, post, viewerId))
                    .collect(Collectors.toList());

            String nextCursor = hasMore && !comments.isEmpty() ? comments.get(comments.size() - 1).getId() : null;

            return PaginatedCommentsResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving comments for post: {}", postId, ex);
            throw new AuthException("An error occurred while retrieving comments", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaginatedCommentsResponse getCommentsByReply(String parentCommentId, String postId, int limit, String cursor, String viewerId) {
        try {
            
            CommentEntity parentComment = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (parentComment.getIsDeleted() != null && parentComment.getIsDeleted()) {
                throw new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!hasAccessToPost(post.getAuthorId(), viewerId)) {
                throw new AuthException("You don't have access to view these comments", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

            List<CommentEntity> replies;
            if (cursor == null) {
                replies = commentRepository.findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtDesc(parentCommentId, pageRequest);
            } else {
                replies = commentRepository.findByParentCommentIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(parentCommentId, cursor, pageRequest);
            }

            boolean hasMore = replies.size() > pageSize;
            if (hasMore) {
                replies = replies.subList(0, pageSize);
            }

            List<CommentResponse> data = replies.stream()
                    .map(comment -> mapToResponse(comment, post, viewerId))
                    .collect(Collectors.toList());

            String nextCursor = hasMore && !replies.isEmpty() ? replies.get(replies.size() - 1).getId() : null;

            return PaginatedCommentsResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving replies for comment: {}", parentCommentId, ex);
            throw new AuthException("An error occurred while retrieving replies", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public CommentResponse createComment(String postId, String authorId, CreateCommentRequest createRequest) {
        try {
            
            if (createRequest.getText() == null || createRequest.getText().isBlank()) {
                throw new AuthException("Comment text cannot be blank", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!canCommentOnPost(post.getAuthorId(), authorId)) {
                throw new AuthException("You are not authorized to comment on this post", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            if (createRequest.getParentCommentId() != null) {
                CommentEntity parentComment = commentRepository.findById(createRequest.getParentCommentId())
                        .orElseThrow(() -> new AuthException("Parent comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

                if (parentComment.getIsDeleted() != null && parentComment.getIsDeleted()) {
                    throw new AuthException("Parent comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
                }

                if (!parentComment.getPostId().equals(postId)) {
                    throw new AuthException("Parent comment does not belong to this post", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
                }
            }

            Instant now = Instant.now();
            CommentEntity comment = CommentEntity.builder()
                    .postId(postId)
                    .authorId(authorId)
                    .parentCommentId(createRequest.getParentCommentId())
                    .text(createRequest.getText())
                    .tagged(createRequest.getTagged())
                    .likeCount(0L)
                    .replyCount(0L)
                    .createdAt(now)
                    .isDeleted(false)
                    .build();

            CommentEntity saved = commentRepository.save(comment);

            if (createRequest.getParentCommentId() != null) {
                commentRepository.findById(createRequest.getParentCommentId()).ifPresent(parent -> {
                    parent.setReplyCount((parent.getReplyCount() != null ? parent.getReplyCount() : 0) + 1);
                    commentRepository.save(parent);
                });
            }

            eventService.emitEvent(EventType.COMMENT_CREATED, saved.getId(), Map.of(
                "commentId", saved.getId(),
                "postId", postId,
                "authorId", authorId,
                "text", saved.getText(),
                "parentCommentId", createRequest.getParentCommentId() != null ? createRequest.getParentCommentId() : "",
                "tagged", createRequest.getTagged() != null ? createRequest.getTagged() : List.of(),
                "createdAt", saved.getCreatedAt().toString()
            ));

            return mapToResponse(saved, post, authorId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error creating comment for post: {} by user: {}", postId, authorId, ex);
            throw new AuthException("An error occurred while creating comment", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void deleteComment(String commentId, String requesterId) {
        try {
            CommentEntity comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (comment.getIsDeleted() != null && comment.getIsDeleted()) {
                throw new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            PostEntity post = postRepository.findById(comment.getPostId())
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!requesterId.equals(comment.getAuthorId()) && !requesterId.equals(post.getAuthorId())) {
                throw new AuthException("You are not authorized to delete this comment", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            comment.setIsDeleted(true);
            commentRepository.save(comment);

            if (comment.getParentCommentId() != null) {
                commentRepository.findById(comment.getParentCommentId()).ifPresent(parent -> {
                    if (parent.getReplyCount() != null && parent.getReplyCount() > 0) {
                        parent.setReplyCount(parent.getReplyCount() - 1);
                        commentRepository.save(parent);
                    }
                });
            }

            eventService.emitEvent(EventType.COMMENT_DELETED, commentId, Map.of(
                "commentId", commentId,
                "postId", comment.getPostId(),
                "authorId", comment.getAuthorId()
            ));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error deleting comment: {}", commentId, ex);
            throw new AuthException("An error occurred while deleting comment", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean hasAccessToPost(String postAuthorId, String viewerId) {
        
        if (viewerId == null) {
            return false;
        }

        if (viewerId.equals(postAuthorId)) {
            return true;
        }

        return followRepository.findByFollowerIdAndFollowingId(viewerId, postAuthorId)
                .map(follow -> follow.getStatus() == FollowStatus.approved)
                .orElse(false);
    }

    private boolean canCommentOnPost(String postAuthorId, String userId) {
        
        if (userId.equals(postAuthorId)) {
            return true;
        }

        return followRepository.findByFollowerIdAndFollowingId(userId, postAuthorId)
                .map(follow -> follow.getStatus() == FollowStatus.approved)
                .orElse(false);
    }

    private CommentResponse mapToResponse(CommentEntity comment, PostEntity post, String viewerId) {
        
        UserEntity author = userRepository.findById(comment.getAuthorId()).orElse(null);
        String authorUsername = author != null ? author.getUsername() : "unknown";
        String authorAvatarUrl = null;
        if (author != null && author.getProfile() != null) {
            authorAvatarUrl = author.getProfile().getAvatarUrl();
        }

        Boolean canDelete = viewerId != null && (
                viewerId.equals(comment.getAuthorId()) || 
                (post != null && viewerId.equals(post.getAuthorId()))
        );

        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorUsername(authorUsername)
                .authorAvatarUrl(authorAvatarUrl)
                .text(comment.getText())
                .tagged(comment.getTagged())
                .likeCount(comment.getLikeCount())
                .replyCount(comment.getReplyCount())
                .createdAt(comment.getCreatedAt())
                .parentCommentId(comment.getParentCommentId())
                .canDelete(canDelete)
                .build();
    }
}
