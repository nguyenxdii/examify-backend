package com.examify.examify.backend.dto.room;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SubmissionSummaryResponse {
    private String submissionId;
    private String studentId;
    private String studentName;
    private float score;
    private int totalQuestions;
    private int correctCount;
    private String gradingStatus;
    private LocalDateTime submittedAt;
    private boolean hasPendingEssay;
    private int attemptNumber;
}
