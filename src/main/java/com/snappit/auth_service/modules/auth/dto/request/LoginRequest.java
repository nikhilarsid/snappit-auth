package com.snappit.auth_service.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String usernameOrEmail;
    @NotBlank
    private String password;
}