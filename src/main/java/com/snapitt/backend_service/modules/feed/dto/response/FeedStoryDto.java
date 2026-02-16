package com.snapitt.backend_service.modules.feed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedStoryDto {
    private String feedStoryId;       
    private String creatorUsername;    
    private String creatorAvatarUrl;  
    private String storyId;           
    private Boolean seen;             
    private Instant latestStoryAt;    
}
