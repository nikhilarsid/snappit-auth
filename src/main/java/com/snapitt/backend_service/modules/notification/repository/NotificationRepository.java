package com.snapitt.backend_service.modules.notification.repository;

import com.snapitt.backend_service.modules.notification.model.NotificationEntity;
import com.snapitt.backend_service.modules.notification.model.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<NotificationEntity, String> {

    Optional<NotificationEntity> findById(String id);

    List<NotificationEntity> findByTargetUserIdOrderBySeenAscCreatedAtDesc(String targetUserId, Pageable pageable);

    List<NotificationEntity> findByTargetUserIdAndIdLessThanOrderBySeenAscCreatedAtDesc(
            String targetUserId, String cursor, Pageable pageable);

    void deleteByTargetUserIdAndActorIdAndType(String targetUserId, String actorId, NotificationType type);

    long countByTargetUserIdAndSeen(String targetUserId, Boolean seen);

    List<NotificationEntity> findByTargetUserIdAndSeen(String targetUserId, Boolean seen);
}
