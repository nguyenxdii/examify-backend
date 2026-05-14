package com.examify.examify.backend.dto.room;

import lombok.Data;
import java.time.Instant;

@Data
public class SubmissionSummaryResponse {
    private String submissionId;
    private String studentId;
    private String studentName;
    private float score;
    private int totalQuestions;
    private int correctCount;
    private String gradingStatus;
    private Instant submittedAt;
    private boolean hasPendingEssay;
    private int attemptNumber;
    private int totalAttempts;
    private int maxAttempts;
    private float avgScore;
    private boolean published;
    @com.fasterxml.jackson.annotation.JsonProperty("isGraded")
    private boolean isGraded;
}
