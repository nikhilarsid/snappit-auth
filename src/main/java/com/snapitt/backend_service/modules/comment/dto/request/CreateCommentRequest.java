package com.snapitt.backend_service.modules.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequest {
    @NotBlank(message = "Comment text cannot be blank")
    @Size(min = 1, max = 5000, message = "Comment text must be between 1 and 5000 characters")
    private String text;                    

    private String parentCommentId;         

    private List<String> tagged;            
}
