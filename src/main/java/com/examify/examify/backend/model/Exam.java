package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document(collection = "exams")
public class Exam {
    @Id
    private String id;
    private String teacherId;
    private String title;
    private String description;
    private String subject;
    private String status; // "draft" | "ready" | "shared"
    private Integer duration; // in minutes
    private Integer passScore; // passing score
    private boolean isShuffled;
    private Instant createdAt;
    private Instant updatedAt;
}
