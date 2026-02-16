package com.snapitt.backend_service.modules.story.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryRequest {
    @NotBlank(message = "Media URL cannot be blank")
    private String mediaUrl;       

    @NotNull(message = "Expiration time cannot be null")
    private Instant expiresAt;     
}
