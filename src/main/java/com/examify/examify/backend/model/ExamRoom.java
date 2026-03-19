package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "exam_rooms")
public class ExamRoom {
    @Id
    private String id;
    private String examId;
    private String teacherId;
    
    @Indexed(unique = true)
    private String roomCode;
    
    private String name;
    private String mode; // "exam" | "practice"
    private int durationMinutes;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private int maxAttempts;
    private boolean showAnswerAfter;
    private boolean requireStudentList;
    private String status; // "pending" | "open" | "closed"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
