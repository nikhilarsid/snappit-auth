package com.snapitt.backend_service.modules.feed.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "post_feed")
@CompoundIndexes({
        @CompoundIndex(name = "user_seen_created_idx", def = "{ 'userId': 1, 'seen': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "user_post_unique_idx", def = "{ 'userId': 1, 'postId': 1 }", unique = true)
})
public class PostFeedEntity {
    @Id
    private String id;

    private String userId;          
    private String postId;          
    private String authorId;        
    private Instant createdAt;      
    private Boolean seen;           
    private Instant insertedAt;     
}
