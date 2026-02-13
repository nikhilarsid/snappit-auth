package com.snappit.auth_service.modules.auth.repository;

import com.snappit.auth_service.modules.auth.model.OtpEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface OtpRepository extends MongoRepository<OtpEntity, String> {
    Optional<OtpEntity> findTopByEmailOrderByExpiresAtDesc(String email);
}