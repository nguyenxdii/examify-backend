package com.examify.examify.backend.dto.room;

import lombok.Data;

@Data
public class GradeEssayRequest {
    private String submissionAnswerId;
    private float finalScore;
    private boolean confirm; // true = fully_graded
}
