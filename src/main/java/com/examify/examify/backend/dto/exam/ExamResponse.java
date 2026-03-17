package com.examify.examify.backend.dto.exam;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ExamResponse {
    private String id;
    private String title;
    private String description;
    private String subject;
    private String status;
    private long questionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
