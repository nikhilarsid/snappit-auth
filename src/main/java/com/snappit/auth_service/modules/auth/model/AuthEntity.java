package com.snappit.auth_service.modules.auth.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document(collection = "auth")
@CompoundIndex(name = "user_type_idx", def = "{'userId': 1, 'type': 1}", unique = true)
public class AuthEntity {
    @Id
    private String id;

    @Indexed
    private String userId;

    private AuthType type; // ENUM: LOCAL or GOOGLE

    private String passwordHash;

    @Indexed(unique = true, sparse = true)
    private String googleSub;

    public enum AuthType {
        LOCAL, GOOGLE
    }
}