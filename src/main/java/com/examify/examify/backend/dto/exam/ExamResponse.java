package com.examify.examify.backend.dto.exam;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class ExamResponse {
    private String id;
    private String title;
    private String description;
    private String subject;
    private String status;
    private Integer duration;
    private Integer passScore;
    private boolean isShuffled;
    private long questionCount;
    private Instant createdAt;
    private Instant updatedAt;
}
