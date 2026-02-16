package com.snapitt.backend_service.modules.follow.controller;

import com.snapitt.backend_service.modules.follow.service.FollowService;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowersResponse;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import com.snapitt.backend_service.security.JwtService;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ImportAutoConfiguration({
    JacksonAutoConfiguration.class,
    WebMvcAutoConfiguration.class,
    MockMvcAutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@SpringBootTest(classes = FollowController.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@AutoConfigureMockMvc
public class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowService followService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private UserPrincipal principal;

    @BeforeEach
    public void setup() {
        UserEntity user = UserEntity.builder().id("u1").username("testuser").build();
        principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void createFollow_authenticated_returnsOk() throws Exception {
        doNothing().when(followService).createFollowRequest("u1", "alice");

        mockMvc.perform(post("/api/v1/follow/alice")
                .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"FOLLOW_REQUEST_SENT\"}"));
    }

    @Test
    public void getFollowers_authenticated_returnsData() throws Exception {
        PaginatedFollowersResponse resp = new PaginatedFollowersResponse(java.util.Collections.emptyList(), null);
        when(followService.getFollowers("alice", 20, null, "u1")).thenReturn(resp);

        mockMvc.perform(get("/api/v1/follow/alice/followers?limit=20")
                .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":[],\"nextCursor\":null}"));
    }

    @Test
    public void unfollow_authenticated_returnsOk() throws Exception {
        doNothing().when(followService).unfollow("u1", "alice");

        mockMvc.perform(delete("/api/v1/follow/alice")
                .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"UNFOLLOWED\"}"));
    }
}
