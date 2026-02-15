package com.snapitt.backend_service.modules.profile.dto.request;

import lombok.Data;

import jakarta.validation.constraints.Size;

/**
 * UpdateProfileRequest - Only these fields can be updated:
 * - name (max 100 chars)
 * - bio (max 160 chars)
 * - avatarUrl
 *
 * Restricted fields (cannot be updated): username, email, followersCount, followingCount, createdAt, updatedAt
 * Attempts to update restricted fields will be ignored by the system.
 */
@Data
public class UpdateProfileRequest {
    @Size(max = 100, message = "Name must be under 100 characters")
    private String name;

    @Size(max = 160, message = "Bio must be under 160 characters")
    private String bio;

    private String avatarUrl;
}
