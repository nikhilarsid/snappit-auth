package com.snappit.auth_service.modules.auth.repository;

import com.snappit.auth_service.modules.auth.model.AuthEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface AuthRepository extends MongoRepository<AuthEntity, String> {
    Optional<AuthEntity> findByUserIdAndType(String userId, AuthEntity.AuthType type);
}