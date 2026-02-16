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

    public PostResponse getPost(String postId, String viewerId) {
        try {
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));
            
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

    public PaginatedPostsResponse getPostsByUsername(String username, int limit, String cursor, String viewerId) {
        try {
            UserEntity user = profileService.getUserByUsername(username);
            if (user == null) {
                throw new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

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

    @Transactional
    public PostResponse createPost(String authorId, CreatePostRequest createRequest) {
        try {
            
            if (createRequest.getMediaUrl() == null || createRequest.getMediaUrl().isBlank()) {
                throw new AuthException("Media URL is required", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
            }

            PostEntity post = PostEntity.builder()
                    .authorId(authorId)
                    .mediaUrl(createRequest.getMediaUrl())
                    .caption(createRequest.getCaption())
                    .likeCount(0L)
                    .commentCount(0L)
                    .createdAt(Instant.now())
                    .build();

            PostEntity saved = postRepository.save(post);

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

    @Transactional
    public void deletePost(String postId, String authorId) {
        try {
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!post.getAuthorId().equals(authorId)) {
                throw new AuthException("You are not the author of this post", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }

            Instant deletedAt = Instant.now();

            postRepository.deleteById(postId);

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

    @Transactional
    public void likePost(String postId, String userId) {
        try {
            
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (likeRepository.findByUserIdAndPostId(userId, postId).isPresent()) {
                throw new AuthException("You have already liked this post", "ALREADY_LIKED", HttpStatus.CONFLICT);
            }

            LikeEntity like = LikeEntity.builder()
                    .userId(userId)
                    .postId(postId)
                    .createdAt(Instant.now())
                    .build();

            likeRepository.save(like);

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

    @Transactional
    public void unlikePost(String postId, String userId) {
        try {
            
            PostEntity post = postRepository.findById(postId)
                    .orElseThrow(() -> new AuthException("Post not found", "POST_NOT_FOUND", HttpStatus.NOT_FOUND));

            LikeEntity like = likeRepository.findByUserIdAndPostId(userId, postId)
                    .orElseThrow(() -> new AuthException("You have not liked this post", "NOT_LIKED", HttpStatus.CONFLICT));

            likeRepository.deleteByUserIdAndPostId(userId, postId);

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

    public List<PostResponse> getPostsByIds(List<String> postIds, String viewerId) {
        try {
            if (postIds == null || postIds.isEmpty()) {
                return List.of();
            }

            List<PostEntity> posts = postRepository.findAllById(postIds);

            Map<String, PostEntity> postMap = posts.stream()
                    .collect(Collectors.toMap(PostEntity::getId, p -> p));

            return postIds.stream()
                    .map(postMap::get)
                    .filter(post -> post != null)  
                    .map(post -> mapToResponse(post, viewerId))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            logger.error("Error fetching posts by IDs: {}", postIds, ex);
            throw new AuthException("An error occurred while retrieving posts", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private PostResponse mapToResponse(PostEntity post, String viewerId) {
        
        UserEntity author = userRepository.findById(post.getAuthorId()).orElse(null);
        String authorUsername = author != null ? author.getUsername() : "unknown";
        String authorAvatarUrl = null;
        if (author != null && author.getProfile() != null) {
            authorAvatarUrl = author.getProfile().getAvatarUrl();
        }

        Boolean canDelete = viewerId != null && viewerId.equals(post.getAuthorId());

        Boolean likedByViewer = viewerId != null && likeRepository.findByUserIdAndPostId(viewerId, post.getId()).isPresent();

        return PostResponse.builder()
                .id(post.getId())
                .authorUsername(authorUsername)
                .authorAvatarUrl(authorAvatarUrl)
                .mediaUrl(post.getMediaUrl())
                .caption(post.getCaption())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .likedByViewer(likedByViewer)
                .createdAt(post.getCreatedAt())
                .canDelete(canDelete)
                .build();
    }
}
