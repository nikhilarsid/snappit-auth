package com.snapitt.backend_service.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    
    @NotBlank(message = "ID Token is required")
    private String idToken;
}