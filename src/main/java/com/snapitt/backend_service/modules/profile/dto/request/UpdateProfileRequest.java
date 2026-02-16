package com.snapitt.backend_service.modules.profile.dto.request;

import lombok.Data;

import jakarta.validation.constraints.Size;

@Data
public class UpdateProfileRequest {
    @Size(max = 100, message = "Name must be under 100 characters")
    private String name;

    @Size(max = 160, message = "Bio must be under 160 characters")
    private String bio;

    private String avatarUrl;
}
