package com.snapitt.backend_service.modules.feed.repository;

import com.snapitt.backend_service.modules.feed.model.PostFeedEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PostFeedRepository extends MongoRepository<PostFeedEntity, String> {

    List<PostFeedEntity> findByUserIdOrderBySeenAscCreatedAtDesc(String userId, Pageable pageable);

    List<PostFeedEntity> findByUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(String userId, String cursor, Pageable pageable);

    long deleteByUserIdAndAuthorId(String userId, String authorId);

    long deleteByPostId(String postId);
}
