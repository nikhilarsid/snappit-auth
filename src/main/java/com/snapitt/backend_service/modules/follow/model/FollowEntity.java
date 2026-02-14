package com.snapitt.backend_service.modules.follow.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@Document(collection = "follows")
@CompoundIndexes({
        @CompoundIndex(name = "follower_following_idx", def = "{ 'followerId': 1, 'followingId': 1 }", unique = true),
        @CompoundIndex(name = "follower_status_idx", def = "{ 'followerId': 1, 'status': 1 }"),
        @CompoundIndex(name = "following_status_idx", def = "{ 'followingId': 1, 'status': 1 }")
})
public class FollowEntity {
    @Id
    private String id;

    private String followerId;
    private String followingId;
    private com.snapitt.backend_service.modules.follow.model.FollowStatus status;
    private Instant createdAt;
}
