package com.social.profile.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 50, message = "Name must be between 1 and 50 chars")
    private String name;

    @Size(max = 160, message = "Bio must be under 160 characters")
    private String bio;

    private String avatarUrl;
}