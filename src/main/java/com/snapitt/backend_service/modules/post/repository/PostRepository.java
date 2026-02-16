package com.snapitt.backend_service.modules.post.repository;

import com.snapitt.backend_service.modules.post.model.PostEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends MongoRepository<PostEntity, String> {
    
    List<PostEntity> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

    List<PostEntity> findByAuthorIdAndIdLessThanOrderByCreatedAtDesc(String authorId, String cursorId, Pageable pageable);
}
