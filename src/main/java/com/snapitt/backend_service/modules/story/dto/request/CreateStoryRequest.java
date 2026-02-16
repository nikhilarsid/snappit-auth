package com.snapitt.backend_service.modules.story.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * CreateStoryRequest - DTO for creating a new story
 *
 * Validation Rules:
 * - mediaUrl: required, must be valid URL format (handled via @NotBlank)
 * - expiresAt: required, must be a future timestamp (validation in service)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryRequest {
    @NotBlank(message = "Media URL cannot be blank")
    private String mediaUrl;       // Story media URL (required)

    @NotNull(message = "Expiration time cannot be null")
    private Instant expiresAt;     // When story expires (required)
}
