package com.snapitt.backend_service.modules.auth.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@Document(collection = "otps")
public class OtpEntity {
    @Id
    private String id;

    @Indexed
    private String email;

    private String code;
    private Instant expiresAt;
}