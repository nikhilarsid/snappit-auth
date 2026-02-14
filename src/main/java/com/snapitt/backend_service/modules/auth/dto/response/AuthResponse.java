package com.snapitt.backend_service.modules.auth.dto.response;

import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private UserEntity user;
    private String token;
}