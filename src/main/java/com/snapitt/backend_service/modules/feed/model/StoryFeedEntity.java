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
@Document(collection = "story_feed")
@CompoundIndexes({
        @CompoundIndex(name = "user_seen_latest_idx", def = "{ 'userId': 1, 'seen': 1, 'latestStoryAt': -1 }"),
        @CompoundIndex(name = "user_creator_unique_idx", def = "{ 'userId': 1, 'creatorId': 1 }", unique = true)
})
public class StoryFeedEntity {
    @Id
    private String id;

    private String userId;          
    private String creatorId;       
    private Instant latestStoryAt;  
    private Boolean seen;           
    private Boolean isDeleted;      
}
