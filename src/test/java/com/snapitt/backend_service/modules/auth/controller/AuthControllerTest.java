package com.snapitt.backend_service.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapitt.backend_service.modules.auth.dto.request.LoginRequest;
import com.snapitt.backend_service.modules.auth.dto.request.SignupRequest;
import com.snapitt.backend_service.modules.auth.dto.response.AuthResponse;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        AuthController controller = new AuthController(authService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void signup_endpoint_setsCookie_andReturnsCreated() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setUsername("tester");
        req.setEmail("t@e.com");
        req.setName("Tester");
        req.setPassword("StrongPass1!");

        UserEntity user = UserEntity.builder().id("u1").username("tester").email("t@e.com").build();
        when(authService.signup(any(SignupRequest.class))).thenReturn(new AuthResponse(user, "tok-1"));

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("token"));
    }

    @Test
    void login_endpoint_setsCookie_andReturnsOk() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("tester");
        req.setPassword("StrongPass1!");

        UserEntity user = UserEntity.builder().id("u1").username("tester").email("t@e.com").build();
        when(authService.login(any(LoginRequest.class))).thenReturn(new AuthResponse(user, "tok-2"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("token"));
    }
}
