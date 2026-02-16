package com.snapitt.backend_service.modules.comment.repository;

import com.snapitt.backend_service.modules.comment.model.CommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends MongoRepository<CommentEntity, String> {
    
    List<CommentEntity> findByPostIdAndParentCommentIdNullAndIsDeletedFalseOrderByCreatedAtDesc(String postId, Pageable pageable);

    List<CommentEntity> findByPostIdAndParentCommentIdNullAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(String postId, String cursorId, Pageable pageable);

    List<CommentEntity> findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtDesc(String parentCommentId, Pageable pageable);

    List<CommentEntity> findByParentCommentIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(String parentCommentId, String cursorId, Pageable pageable);

    long countByParentCommentIdAndIsDeletedFalse(String parentCommentId);
}
