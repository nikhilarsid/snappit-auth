package com.snapitt.backend_service.modules.follow.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaginationQueryRequest {
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 50, message = "Limit cannot exceed 50")
    private Integer limit;

    private String cursor;

    public static PaginationQueryRequest of(Integer limit, String cursor) {
        return new PaginationQueryRequest(
                limit != null ? limit : 20,
                cursor
        );
    }
}
