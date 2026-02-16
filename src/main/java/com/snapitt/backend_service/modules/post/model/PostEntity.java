package com.snapitt.backend_service.modules.post.model;

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
@Document(collection = "posts")
@CompoundIndexes({
        @CompoundIndex(name = "author_created_idx", def = "{ 'authorId': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "created_idx", def = "{ 'createdAt': -1 }")
})
public class PostEntity {
    @Id
    private String id;

    private String authorId;        
    private String mediaUrl;        
    private String caption;         
    private Long likeCount;         
    private Long commentCount;      
    private Instant createdAt;      
}
