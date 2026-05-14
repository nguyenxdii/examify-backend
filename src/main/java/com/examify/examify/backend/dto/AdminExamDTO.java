package com.examify.examify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class AdminExamDTO {
    private String id;
    private String title;
    private String teacherName;
    private String teacherEmail;
    private int questionCount;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
