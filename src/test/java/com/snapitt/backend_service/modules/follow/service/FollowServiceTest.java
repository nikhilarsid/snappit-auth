package com.snapitt.backend_service.modules.follow.service;

import com.snapitt.backend_service.modules.event.service.EventService;
import com.snapitt.backend_service.modules.follow.dto.response.PaginatedFollowersResponse;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.follow.model.FollowEntity;
import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.profile.service.ProfileService;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private EventService eventService;

    private com.snapitt.backend_service.modules.follow.service.FollowService followService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        followService = new com.snapitt.backend_service.modules.follow.service.FollowService(followRepository, profileService, eventService);
    }

    @Test
    public void getFollowers_emptyRelations_returnsEmptyData() {
        String username = "alice";
        UserEntity user = UserEntity.builder().id("u1").username(username).build();

        when(profileService.getUserByUsername(username)).thenReturn(user);
        when(followRepository.findByFollowingIdAndStatusOrderByIdDesc(org.mockito.ArgumentMatchers.eq(user.getId()), org.mockito.ArgumentMatchers.eq(FollowStatus.approved), org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(Collections.emptyList());

        PaginatedFollowersResponse result = followService.getFollowers(username, 20, null, "viewer");

        assertEquals(null, result.getNextCursor());
        assertEquals(Collections.emptyList(), result.getData());
    }
}
