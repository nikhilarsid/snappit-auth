package com.snapitt.backend_service.modules.story.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stories")
@CompoundIndexes({
        @CompoundIndex(name = "author_created_idx", def = "{ 'authorId': 1, 'createdAt': -1 }"),
})
public class StoryEntity {
    @Id
    private String id;

    private String authorId;        
    private String mediaUrl;        
    private Instant createdAt;      
    
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;      
    
    private Boolean isDeleted;      
    private Instant deletedAt;      
}
