package com.snapitt.backend_service.modules.comment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {
    private String id;                      
    private String postId;                  
    private String authorUsername;
    private String authorAvatarUrl;
    private String text;                    
    private List<String> tagged;            
    private Long likeCount;                 
    private Long replyCount;                
    private Instant createdAt;              
    private String parentCommentId;         
    private Boolean canDelete;              
}
