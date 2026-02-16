package com.snapitt.backend_service.modules.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {
    @NotBlank(message = "Media URL cannot be blank")
    private String mediaUrl;       

    @Size(max = 500, message = "Caption must be under 500 characters")
    private String caption;         
}
