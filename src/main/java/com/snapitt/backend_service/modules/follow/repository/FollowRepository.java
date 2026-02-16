package com.snapitt.backend_service.modules.follow.repository;

import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FollowRepository extends MongoRepository<FollowEntity, String> {

    Optional<FollowEntity> findByFollowerIdAndFollowingId(String followerId, String followingId);

    List<FollowEntity> findByFollowingIdAndStatusOrderByIdDesc(String followingId, FollowStatus status, Pageable pageable);

    List<FollowEntity> findByFollowingIdAndStatusAndIdLessThanOrderByIdDesc(String followingId, FollowStatus status, String idCursor, Pageable pageable);

    List<FollowEntity> findByFollowerIdAndStatusOrderByIdDesc(String followerId, FollowStatus status, Pageable pageable);

    List<FollowEntity> findByFollowerIdAndStatusAndIdLessThanOrderByIdDesc(String followerId, FollowStatus status, String idCursor, Pageable pageable);

    List<FollowEntity> findByFollowerIdAndFollowingIdIn(String followerId, List<String> followingIds);

    void deleteByFollowerIdAndFollowingId(String followerId, String followingId);
}
