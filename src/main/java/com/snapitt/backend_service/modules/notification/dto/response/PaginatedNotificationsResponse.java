package com.snapitt.backend_service.modules.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PaginatedNotificationsResponse - Response for GET /api/v1/notifications
 *
 * Provides cursor-paginated list of notifications for authenticated user.
 * Ordered by: unseen first (ascending), then newest notifications first (descending by createdAt).
 *
 * Fields:
 * - data: List of NotificationDto items
 * - nextCursor: Cursor for next page (null if no more pages)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedNotificationsResponse {
    private List<NotificationDto> data;
    private String nextCursor;
}
