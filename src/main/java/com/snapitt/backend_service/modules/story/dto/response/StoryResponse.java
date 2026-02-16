package com.snapitt.backend_service.modules.story.dto.response;

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
public class StoryResponse {
    private String id;              
    private String authorUsername;  
    private String mediaUrl;        
    private Instant createdAt;      
    private Instant expiresAt;      
    private Boolean canDelete;      
}
