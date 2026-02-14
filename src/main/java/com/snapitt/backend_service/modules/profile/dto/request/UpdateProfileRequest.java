package com.snapitt.backend_service.modules.profile.dto.request;

import lombok.Data;

import jakarta.validation.constraints.Size;

@Data
public class UpdateProfileRequest {
    @Size(max = 100)
    private String name;

    @Size(max = 160)
    private String bio;

    private String avatarUrl;
}
