package com.snapitt.backend_service.modules.post.dto.response;

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
public class PostResponse {
    private String id;              
    private String authorUsername;
    private String authorAvatarUrl;
    private String mediaUrl;        
    private String caption;         
    private Long likeCount;         
    private Long commentCount;      
    private Boolean likedByViewer;  
    private Instant createdAt;      
    private Boolean canDelete;      
}
