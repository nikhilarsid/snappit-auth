package com.snapitt.backend_service.modules.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CreateCommentRequest - DTO for creating a new comment
 *
 * Validation Rules:
 * - text: required, 1-5000 characters
 * - parentCommentId: optional, for replies
 * - tagged: optional, list of user IDs to tag
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequest {
    @NotBlank(message = "Comment text cannot be blank")
    @Size(min = 1, max = 5000, message = "Comment text must be between 1 and 5000 characters")
    private String text;                    // Comment content (required)

    private String parentCommentId;         // Parent comment ID for replies (optional)

    private List<String> tagged;            // List of user IDs to tag (optional)
}
