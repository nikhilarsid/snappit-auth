package com.snappit.auth_service.modules.auth.dto.response;

import com.snappit.auth_service.modules.user.model.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private UserEntity user;
    private String token;
}