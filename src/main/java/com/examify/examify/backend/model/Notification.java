package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;
    private String userId;
    private String title;
    private String message;
    private String type; // INFO, SUCCESS, WARNING, DANGER
    private boolean isRead = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}
