package com.social.profile.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    //  UNIQUE INDEX 1: Username
    @Indexed(unique = true)
    private String username;

    //  UNIQUE INDEX 2: Email (Added this!)
    @Indexed(unique = true)
    private String email;

    private ProfileData profile;

    // Stats (Read-only for users)
    private Integer followersCount = 0;
    private Integer followingCount = 0;

    // TIMESTAMPS (Auto-managed by Spring)
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}