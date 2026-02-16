package com.snapitt.backend_service.modules.post.repository;

import com.snapitt.backend_service.modules.post.model.LikeEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LikeRepository extends MongoRepository<LikeEntity, String> {
    
    Optional<LikeEntity> findByUserIdAndPostId(String userId, String postId);

    void deleteByUserIdAndPostId(String userId, String postId);

    long countByPostId(String postId);
}
