package com.snapitt.backend_service.modules.search.controller;

import com.snapitt.backend_service.modules.search.dto.SearchResultDTO;
import com.snapitt.backend_service.modules.search.service.SearchService;
import com.snapitt.backend_service.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<List<SearchResultDTO>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {

        String viewerId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            viewerId = ((UserPrincipal) auth.getPrincipal()).getUser().getId();
        }

        List<SearchResultDTO> results = searchService.search(query, limit, viewerId);
        return ResponseEntity.ok(results);
    }
}
