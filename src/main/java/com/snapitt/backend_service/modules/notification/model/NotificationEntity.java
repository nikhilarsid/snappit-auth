package com.snapitt.backend_service.modules.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "target_seen_created_idx", def = "{ 'targetUserId': 1, 'seen': 1, 'createdAt': -1 }")
})
public class NotificationEntity {
    @Id
    private String id;

    private String targetUserId;           
    private String actorId;                
    private NotificationType type;         
    private String entityId;               
    private Boolean seen;                  
    private Instant createdAt;             
}
