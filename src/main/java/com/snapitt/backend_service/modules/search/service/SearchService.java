package com.snapitt.backend_service.modules.search.service;

import com.snapitt.backend_service.modules.follow.model.FollowStatus;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.search.dto.SearchResultDTO;
import com.snapitt.backend_service.modules.search.repository.SearchRepository;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final SearchRepository searchRepository;
    private final FollowRepository followRepository;

    public List<SearchResultDTO> search(String query, int limit, String viewerId) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        int effectiveLimit = Math.max(1, Math.min(limit, 50));

        List<UserEntity> users;
        try {
            users = searchRepository.searchUsers(query, effectiveLimit);
        } catch (Exception ex) {
            log.warn("Atlas Search failed, falling back to regex search: {}", ex.getMessage());
            try {
                users = searchRepository.searchUsersFallback(query, effectiveLimit);
            } catch (Exception fallbackEx) {
                log.error("Fallback search also failed: {}", fallbackEx.getMessage());
                return List.of();
            }
        }

        return users.stream()
                .map(user -> mapToSearchResult(user, viewerId))
                .collect(Collectors.toList());
    }

    private SearchResultDTO mapToSearchResult(UserEntity user, String viewerId) {
        boolean isFollowing = false;
        if (viewerId != null && !viewerId.equals(user.getId())) {
            try {
                var relOpt = followRepository.findByFollowerIdAndFollowingId(viewerId, user.getId());
                isFollowing = relOpt.isPresent()
                        && FollowStatus.approved.equals(relOpt.get().getStatus());
            } catch (Exception e) {
                log.warn("Error checking follow status for {} -> {}", viewerId, user.getId());
            }
        }

        String name = user.getProfile() != null ? user.getProfile().getName() : null;
        String avatarUrl = user.getProfile() != null ? user.getProfile().getAvatarUrl() : null;

        return SearchResultDTO.builder()
                .username(user.getUsername())
                .name(name)
                .avatarUrl(avatarUrl)
                .followersCount(user.getFollowersCount())
                .followingCount(user.getFollowingCount())
                .isFollowing(isFollowing)
                .build();
    }
}
