package com.social.profile.service.client;

import org.springframework.stereotype.Component;

@Component
public class FollowServiceClient {

    // Simulating a call to Follow Service
    // In real life, use @FeignClient here
    public boolean isFollowing(String viewerId, String targetId) {
        // TODO: Make HTTP call to Follow Service
        // For now, return false or mock logic
        return false;
    }
}