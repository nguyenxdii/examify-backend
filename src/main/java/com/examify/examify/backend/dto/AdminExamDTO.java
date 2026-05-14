package com.examify.examify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminExamDTO {
    private String id;
    private String title;
    private String teacherName;
    private String teacherEmail;
    private int questionCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
