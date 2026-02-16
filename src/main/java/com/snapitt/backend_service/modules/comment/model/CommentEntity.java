package com.snapitt.backend_service.modules.comment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "comments")
@CompoundIndexes({
        @CompoundIndex(name = "post_parent_created_idx", def = "{ 'postId': 1, 'parentCommentId': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "parent_created_idx", def = "{ 'parentCommentId': 1, 'createdAt': -1 }")
})
public class CommentEntity {
    @Id
    private String id;

    private String postId;              
    private String authorId;            
    private String parentCommentId;     
    private String text;                
    private List<String> tagged;        
    private Long likeCount;             
    private Long replyCount;            
    private Instant createdAt;          
    private Boolean isDeleted;          
}
