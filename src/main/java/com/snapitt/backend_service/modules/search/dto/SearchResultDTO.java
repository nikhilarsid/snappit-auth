package com.snapitt.backend_service.modules.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResultDTO {
    private String username;
    private String name;
    private String avatarUrl;
    private Long followersCount;
    private Long followingCount;
    private Boolean isFollowing;
}
