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

/**
 * CommentService - Handles comment operations with transactional event emission
 *
 * Business Logic:
 * - Get comment: User must have access to the post (author or approved follower)
 * - Get comments by post: User must have access to the post
 * - Get replies: User must have access to the post
 * - Create comment: User must have access to post author (author or approved follower)
 * - Delete comment: Soft delete (no event emitted). Only author or post author can delete
 *
 * Exception Handling:
 * - AuthException: All business logic exceptions with specific error codes
 * - Generic Exception: Caught and wrapped as INTERNAL_SERVER_ERROR
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final EventService eventService;

    /**
     * Get comment by ID with access control
     * @param commentId MongoDB ObjectId
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return CommentResponse with comment details
     * @throws AuthException (COMMENT_NOT_FOUND, 404) - Comment not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to the post
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public CommentResponse getComment(String commentId, String viewerId) {
        try {
            CommentEntity comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check if comment is deleted
            if (comment.getIsDeleted() != null && comment.getIsDeleted()) {
                throw new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            // Check access to the post
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

    /**
     * Get paginated top-level comments for a post with access control
     * @param postId Post ID
     * @param limit Items per page (1-50)
     * @param cursor Opaque cursor for pagination
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return PaginatedCommentsResponse with comments and nextCursor
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to the post
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public PaginatedCommentsResponse getCommentsByPost(String postId, int limit, String cursor, String viewerId) {
        try {
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check access to view this post's comments
            if (!hasAccessToPost(post.getAuthorId(), viewerId)) {
                throw new AuthException("You don't have access to view these comments", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            List<CommentEntity> comments;
            if (cursor == null) {
                comments = commentRepository.findByPostIdAndParentCommentIdNullAndIsDeletedFalseOrderByCreatedAtDesc(postId, pageRequest);
            } else {
                comments = commentRepository.findByPostIdAndParentCommentIdNullAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(postId, cursor, pageRequest);
            }

            List<CommentResponse> data = comments.stream()
                    .map(comment -> mapToResponse(comment, post, viewerId))
                    .collect(Collectors.toList());

            String nextCursor = comments.isEmpty() ? null : comments.get(comments.size() - 1).getId();

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

    /**
     * Get paginated replies to a comment with access control
     * @param parentCommentId Parent comment ID
     * @param postId Post ID (for access control)
     * @param limit Items per page (1-50)
     * @param cursor Opaque cursor for pagination
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return PaginatedCommentsResponse with replies and nextCursor
     * @throws AuthException (COMMENT_NOT_FOUND, 404) - Parent comment not found
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to the post
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public PaginatedCommentsResponse getCommentsByReply(String parentCommentId, String postId, int limit, String cursor, String viewerId) {
        try {
            // Verify parent comment exists and is not deleted
            CommentEntity parentComment = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (parentComment.getIsDeleted() != null && parentComment.getIsDeleted()) {
                throw new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check access to view this post's comments
            if (!hasAccessToPost(post.getAuthorId(), viewerId)) {
                throw new AuthException("You don't have access to view these comments", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            List<CommentEntity> replies;
            if (cursor == null) {
                replies = commentRepository.findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtDesc(parentCommentId, pageRequest);
            } else {
                replies = commentRepository.findByParentCommentIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(parentCommentId, cursor, pageRequest);
            }

            List<CommentResponse> data = replies.stream()
                    .map(comment -> mapToResponse(comment, post, viewerId))
                    .collect(Collectors.toList());

            String nextCursor = replies.isEmpty() ? null : replies.get(replies.size() - 1).getId();

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

    /**
     * Create a new comment or reply
     * Transactional: Save comment → Emit COMMENT_CREATED event
     * Background job will handle notifications
     *
     * Access Control:
     * - User must be post author OR approved follower of post author
     *
     * @param postId Post ID to comment on
     * @param authorId User ID of comment author
     * @param createRequest Comment creation request
     * @return CommentResponse with created comment
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Not authorized to comment on this post
     * @throws AuthException (VALIDATION_ERROR, 400) - text blank or too long
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public CommentResponse createComment(String postId, String authorId, CreateCommentRequest createRequest) {
        try {
            // Validate text
            if (createRequest.getText() == null || createRequest.getText().isBlank()) {
                throw new AuthException("Comment text cannot be blank", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            // Get post
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check access to comment on this post (must be post author or approved follower)
            if (!canCommentOnPost(post.getAuthorId(), authorId)) {
                throw new AuthException("You are not authorized to comment on this post", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            // If replying to a comment, verify parent comment exists and is not deleted
            if (createRequest.getParentCommentId() != null) {
                CommentEntity parentComment = commentRepository.findById(createRequest.getParentCommentId())
                        .orElseThrow(() -> new AuthException("Parent comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

                if (parentComment.getIsDeleted() != null && parentComment.getIsDeleted()) {
                    throw new AuthException("Parent comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
                }

                // Verify parent comment belongs to same post
                if (!parentComment.getPostId().equals(postId)) {
                    throw new AuthException("Parent comment does not belong to this post", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
                }
            }

            // Create comment entity
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

            // If this is a reply, increment reply count of parent comment
            if (createRequest.getParentCommentId() != null) {
                commentRepository.findById(createRequest.getParentCommentId()).ifPresent(parent -> {
                    parent.setReplyCount((parent.getReplyCount() != null ? parent.getReplyCount() : 0) + 1);
                    commentRepository.save(parent);
                });
            }

            // Emit COMMENT_CREATED event for background job to handle notifications
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

    /**
     * Delete a comment (soft delete)
     * Only the comment author or the post author can delete
     *
     * @param commentId Comment ID to delete
     * @param requesterId User ID requesting deletion
     * @throws AuthException (COMMENT_NOT_FOUND, 404) - Comment not found or already deleted
     * @throws AuthException (FORBIDDEN, 403) - Not authorized to delete this comment
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public void deleteComment(String commentId, String requesterId) {
        try {
            CommentEntity comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check if already deleted
            if (comment.getIsDeleted() != null && comment.getIsDeleted()) {
                throw new AuthException("Comment not found", "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            // Get post to check if requester is post author
            PostEntity post = postRepository.findById(comment.getPostId())
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check authorization: must be comment author or post author
            if (!requesterId.equals(comment.getAuthorId()) && !requesterId.equals(post.getAuthorId())) {
                throw new AuthException("You are not authorized to delete this comment", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            // Soft delete - mark isDeleted = true
            comment.setIsDeleted(true);
            commentRepository.save(comment);

            // If this is a reply, decrement reply count of parent comment
            if (comment.getParentCommentId() != null) {
                commentRepository.findById(comment.getParentCommentId()).ifPresent(parent -> {
                    if (parent.getReplyCount() != null && parent.getReplyCount() > 0) {
                        parent.setReplyCount(parent.getReplyCount() - 1);
                        commentRepository.save(parent);
                    }
                });
            }

            // Note: No event emitted for delete (as per requirements)
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error deleting comment: {}", commentId, ex);
            throw new AuthException("An error occurred while deleting comment", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check if viewer has access to view posts/comments from a specific post author
     * Access is granted if:
     * - Viewer is the post author themselves, OR
     * - Viewer is an approved follower of the post author
     *
     * @param postAuthorId ID of the post author
     * @param viewerId ID of the viewer (null if not authenticated)
     * @return true if viewer has access, false otherwise
     */
    private boolean hasAccessToPost(String postAuthorId, String viewerId) {
        // If not authenticated, deny access
        if (viewerId == null) {
            return false;
        }

        // If viewing own posts, allow
        if (viewerId.equals(postAuthorId)) {
            return true;
        }

        // Check if viewer is an approved follower of the post author
        return followRepository.findByFollowerIdAndFollowingId(viewerId, postAuthorId)
                .map(follow -> follow.getStatus() == FollowStatus.approved)
                .orElse(false);
    }

    /**
     * Check if user can comment on a post
     * User can comment if:
     * - User is the post author OR
     * - User is an approved follower of the post author
     *
     * @param postAuthorId ID of the post author
     * @param userId ID of the user attempting to comment
     * @return true if user can comment, false otherwise
     */
    private boolean canCommentOnPost(String postAuthorId, String userId) {
        // If user is the post author, allow
        if (userId.equals(postAuthorId)) {
            return true;
        }

        // Check if user is an approved follower of the post author
        return followRepository.findByFollowerIdAndFollowingId(userId, postAuthorId)
                .map(follow -> follow.getStatus() == FollowStatus.approved)
                .orElse(false);
    }

    /**
     * Map CommentEntity to CommentResponse with viewer context
     * @param comment CommentEntity to map
     * @param post PostEntity for context
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return CommentResponse with authorUsername and canDelete flag
     */
    private CommentResponse mapToResponse(CommentEntity comment, PostEntity post, String viewerId) {
        // Fetch author to get username
        String authorUsername = userRepository.findById(comment.getAuthorId())
                .map(user -> user.getUsername())
                .orElse("unknown");

        // Determine if viewer can delete (comment author or post author)
        Boolean canDelete = viewerId != null && (
                viewerId.equals(comment.getAuthorId()) || 
                (post != null && viewerId.equals(post.getAuthorId()))
        );

        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorUsername(authorUsername)
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
