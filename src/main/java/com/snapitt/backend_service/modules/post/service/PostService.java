package com.snapitt.backend_service.modules.post.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.event.model.EventType;
import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.post.dto.request.CreatePostRequest;
import com.snapitt.backend_service.modules.post.dto.response.PaginatedPostsResponse;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
import com.snapitt.backend_service.modules.post.model.PostEntity;
import com.snapitt.backend_service.modules.post.model.LikeEntity;
import com.snapitt.backend_service.modules.post.repository.PostRepository;
import com.snapitt.backend_service.modules.post.repository.LikeRepository;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.user.model.UserEntity;
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
 * PostService - Handles post operations with transactional event emission
 *
 * Business Logic:
 * - Create: Save post → Emit POST_CREATED event (BG job handles post_feed fanout)
 * - Delete: Hard delete post → Emit POST_DELETED event (BG job handles post_feed cleanup)
 * - Like/Unlike: Create/delete like record → Emit event (BG job updates likeCount)
 *
 * Exception Handling:
 * - AuthException: All business logic exceptions with specific error codes
 * - Generic Exception: Caught and wrapped as INTERNAL_SERVER_ERROR
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private static final Logger logger = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final ProfileService profileService;
    private final EventService eventService;
    private final com.snapitt.backend_service.modules.user.repository.UserRepository userRepository;

    /**
     * Get post by ID with access control
     * @param postId MongoDB ObjectId
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return PostResponse with post details
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to this post
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public PostResponse getPost(String postId, String viewerId) {
        try {
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));
            
            // Check access
            if (!hasAccessToPost(post.getAuthorId(), viewerId)) {
                throw new AuthException("You don't have access to view this post", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }
            
            return mapToResponse(post, viewerId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving post: {}", postId, ex);
            throw new AuthException("An error occurred while retrieving post", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get paginated posts by username with access control
     * @param username Author's username
     * @param limit Items per page (1-50)
     * @param cursor Opaque cursor for pagination
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return PaginatedPostsResponse with posts and nextCursor
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (FORBIDDEN, 403) - Viewer doesn't have access to view these posts
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    public PaginatedPostsResponse getPostsByUsername(String username, int limit, String cursor, String viewerId) {
        try {
            UserEntity user = profileService.getUserByUsername(username);
            if (user == null) {
                throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            // Check access to view this user's posts
            if (!hasAccessToPost(user.getId(), viewerId)) {
                throw new AuthException("You don't have access to view these posts", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            List<PostEntity> posts;
            if (cursor == null) {
                posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId(), pageRequest);
            } else {
                posts = postRepository.findByAuthorIdAndIdLessThanOrderByCreatedAtDesc(user.getId(), cursor, pageRequest);
            }

            List<PostResponse> data = posts.stream()
                    .map(post -> mapToResponse(post, viewerId))
                    .collect(Collectors.toList());

            String nextCursor = posts.isEmpty() ? null : posts.get(posts.size() - 1).getId();

            return PaginatedPostsResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving posts for username: {}", username, ex);
            throw new AuthException("An error occurred while retrieving posts", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get a post that belongs to the given userId (authenticated user's own post)
     * @param postId Post ID to fetch
     * @param userId Owner's user ID (from JWT)
     * @return PostResponse
     */
    public PostResponse getMyPost(String postId, String userId) {
        try {
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!post.getAuthorId().equals(userId)) {
                throw new AuthException("You don't have access to this post", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            return mapToResponse(post, userId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving my post: {} for user: {}", postId, userId, ex);
            throw new AuthException("An error occurred while retrieving post", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get paginated posts for the authenticated user
     * @param userId Owner's user ID (from JWT)
     * @param limit page size
     * @param cursor opaque cursor
     * @return PaginatedPostsResponse
     */
    public PaginatedPostsResponse getMyPosts(String userId, int limit, String cursor) {
        try {
            int pageSize = Math.max(1, Math.min(limit, 50));
            PageRequest pageRequest = PageRequest.of(0, pageSize);

            List<PostEntity> posts;
            if (cursor == null) {
                posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId, pageRequest);
            } else {
                posts = postRepository.findByAuthorIdAndIdLessThanOrderByCreatedAtDesc(userId, cursor, pageRequest);
            }

            List<PostResponse> data = posts.stream()
                    .map(post -> mapToResponse(post, userId))
                    .collect(Collectors.toList());

            String nextCursor = posts.isEmpty() ? null : posts.get(posts.size() - 1).getId();

            return PaginatedPostsResponse.builder()
                    .data(data)
                    .nextCursor(nextCursor)
                    .build();
        } catch (Exception ex) {
            logger.error("Error retrieving my posts for user: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving posts", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Create a new post
     * Transactional: Save post → Emit POST_CREATED event
     * Background job will handle post_feed fanout
     *
     * @param authorId User ID of post author
     * @param createRequest Post creation request
     * @return PostResponse with created post
     * @throws AuthException (VALIDATION_ERROR, 400) - mediaUrl blank or caption too long
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public PostResponse createPost(String authorId, CreatePostRequest createRequest) {
        try {
            // Validate mediaUrl (required, not blank)
            if (createRequest.getMediaUrl() == null || createRequest.getMediaUrl().isBlank()) {
                throw new AuthException("Media URL is required", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            // Create post entity
            PostEntity post = PostEntity.builder()
                    .authorId(authorId)
                    .mediaUrl(createRequest.getMediaUrl())
                    .caption(createRequest.getCaption())
                    .likeCount(0L)
                    .commentCount(0L)
                    .createdAt(Instant.now())
                    .build();

            PostEntity saved = postRepository.save(post);

            // Emit POST_CREATED event for background job to handle post_feed fanout
            eventService.emitEvent(EventType.POST_CREATED, saved.getId(), Map.of(
                "postId", saved.getId(),
                "authorId", authorId,
                "mediaUrl", saved.getMediaUrl(),
                "caption", saved.getCaption() != null ? saved.getCaption() : "",
                "createdAt", saved.getCreatedAt().toString()
            ));

            return mapToResponse(saved, authorId);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error creating post for author: {}", authorId, ex);
            throw new AuthException("An error occurred while creating post", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete a post (hard delete)
     * Transactional: Hard delete post → Emit POST_DELETED event
     * Background job will handle post_feed cleanup
     *
     * @param postId Post ID to delete
     * @param authorId User ID requesting deletion (must be author)
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (FORBIDDEN, 403) - Not post author
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public void deletePost(String postId, String authorId) {
        try {
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Verify ownership
            if (!post.getAuthorId().equals(authorId)) {
                throw new AuthException("You are not the author of this post", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            Instant deletedAt = Instant.now();

            // Hard delete from database
            postRepository.deleteById(postId);

            // Emit POST_DELETED event with deletedAt timestamp
            // Background job will handle post_feed cleanup
            eventService.emitEvent(EventType.POST_DELETED, postId, Map.of(
                "postId", postId,
                "authorId", authorId,
                "deletedAt", deletedAt.toString()
            ));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error deleting post: {}", postId, ex);
            throw new AuthException("An error occurred while deleting post", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Like a post
     * Transactional: Create like record → Emit POST_LIKED event
     * Background job will handle likeCount increment
     *
     * @param postId Post ID to like
     * @param userId User ID requesting like
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (ALREADY_LIKED, 409) - Already liked this post
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public void likePost(String postId, String userId) {
        try {
            // Verify post exists
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check if already liked
            if (likeRepository.findByUserIdAndPostId(userId, postId).isPresent()) {
                throw new AuthException("You have already liked this post", "ALREADY_LIKED", HttpStatus.CONFLICT);
            }

            // Create like record
            LikeEntity like = LikeEntity.builder()
                    .userId(userId)
                    .postId(postId)
                    .createdAt(Instant.now())
                    .build();

            likeRepository.save(like);

            // Emit POST_LIKED event for background job to update likeCount
            eventService.emitEvent(EventType.POST_LIKED, postId, Map.of(
                "postId", postId,
                "userId", userId,
                "authorId", post.getAuthorId(),
                "likedAt", Instant.now().toString()
            ));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error liking post: {} by user: {}", postId, userId, ex);
            throw new AuthException("An error occurred while liking post", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Unlike a post
     * Transactional: Delete like record → Emit POST_UNLIKED event
     * Background job will handle likeCount decrement
     *
     * @param postId Post ID to unlike
     * @param userId User ID requesting unlike
     * @throws AuthException (POST_NOT_FOUND, 404) - Post not found
     * @throws AuthException (NOT_LIKED, 409) - Post not currently liked by user
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Unexpected errors
     */
    @Transactional
    public void unlikePost(String postId, String userId) {
        try {
            // Verify post exists
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            // Check if like exists
            LikeEntity like = likeRepository.findByUserIdAndPostId(userId, postId)
                    .orElseThrow(() -> new AuthException("You have not liked this post", "NOT_LIKED", HttpStatus.CONFLICT));

            // Delete like record
            likeRepository.deleteByUserIdAndPostId(userId, postId);

            // Emit POST_UNLIKED event for background job to update likeCount
            eventService.emitEvent(EventType.POST_UNLIKED, postId, Map.of(
                "postId", postId,
                "userId", userId,
                "authorId", post.getAuthorId(),
                "unlikedAt", Instant.now().toString()
            ));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error unliking post: {} by user: {}", postId, userId, ex);
            throw new AuthException("An error occurred while unliking post", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check if viewer has access to view posts from a specific author
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
     * Map PostEntity to PostResponse with viewer context
     * @param post PostEntity to map
     * @param viewerId Viewer's user ID (optional, null if not authenticated)
     * @return PostResponse with authorUsername and canDelete flag
     */
    private PostResponse mapToResponse(PostEntity post, String viewerId) {
        // Fetch author to get username
        String authorUsername = userRepository.findById(post.getAuthorId())
                .map(user -> user.getUsername())
                .orElse("unknown");

        // Determine if viewer can delete (only if viewer is the author)
        Boolean canDelete = viewerId != null && viewerId.equals(post.getAuthorId());

        return PostResponse.builder()
                .id(post.getId())
                .authorUsername(authorUsername)
                .mediaUrl(post.getMediaUrl())
                .caption(post.getCaption())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .canDelete(canDelete)
                .build();
    }
}
