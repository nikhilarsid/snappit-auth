package com.snapitt.backend_service.modules.feed.repository;

import com.snapitt.backend_service.modules.feed.model.StoryFeedEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StoryFeedRepository extends MongoRepository<StoryFeedEntity, String> {

    List<StoryFeedEntity> findByUserIdAndIsDeletedFalseOrderBySeenAscLatestStoryAtDesc(String userId, Pageable pageable);

    List<StoryFeedEntity> findByUserIdAndIsDeletedFalseAndIdLessThanOrderBySeenAscLatestStoryAtDesc(String userId, String cursor, Pageable pageable);

    long deleteByUserIdAndCreatorId(String userId, String creatorId);

    java.util.Optional<StoryFeedEntity> findByUserIdAndCreatorIdAndIsDeletedFalse(String userId, String creatorId);
}
