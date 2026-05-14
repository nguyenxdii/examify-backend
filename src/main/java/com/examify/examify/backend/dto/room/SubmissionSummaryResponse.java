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
    private int totalAttempts;
    private int maxAttempts;
    private float avgScore;
    private boolean published;
    @com.fasterxml.jackson.annotation.JsonProperty("isGraded")
    private boolean isGraded;
}
