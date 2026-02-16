package com.snapitt.backend_service.modules.comment.repository;

import com.snapitt.backend_service.modules.comment.model.CommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CommentRepository - Data access layer for comments
 *
 * Query methods:
 * - findByPostIdAndParentCommentIdNullAndIsDeletedFalseOrderByCreatedAtDesc: Get top-level comments for a post
 * - findByPostIdAndParentCommentIdNullAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc: Cursor pagination for top-level comments
 * - findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtDesc: Get replies to a comment
 * - findByParentCommentIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc: Cursor pagination for replies
 */
@Repository
public interface CommentRepository extends MongoRepository<CommentEntity, String> {
    /**
     * Find all top-level comments (parentCommentId is null) for a post, sorted by creation date (newest first)
     * Filters out deleted comments (isDeleted=false)
     */
    List<CommentEntity> findByPostIdAndParentCommentIdNullAndIsDeletedFalseOrderByCreatedAtDesc(String postId, Pageable pageable);

    /**
     * Find top-level comments for a post with cursor-based pagination
     * Returns comments created before the cursor ID
     */
    List<CommentEntity> findByPostIdAndParentCommentIdNullAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(String postId, String cursorId, Pageable pageable);

    /**
     * Find all replies to a comment, sorted by creation date (newest first)
     * Filters out deleted comments (isDeleted=false)
     */
    List<CommentEntity> findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtDesc(String parentCommentId, Pageable pageable);

    /**
     * Find replies to a comment with cursor-based pagination
     * Returns comments created before the cursor ID
     */
    List<CommentEntity> findByParentCommentIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(String parentCommentId, String cursorId, Pageable pageable);

    /**
     * Count active (non-deleted) replies to a comment
     */
    long countByParentCommentIdAndIsDeletedFalse(String parentCommentId);
}
