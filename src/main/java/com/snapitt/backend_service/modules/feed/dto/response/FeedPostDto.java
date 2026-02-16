package com.snapitt.backend_service.modules.feed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.snapitt.backend_service.modules.post.dto.response.PostResponse;
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
public class FeedPostDto {
    private String id;               
    private PostResponse post;       
    private Boolean seen;            
    private Instant createdAt;       
}
