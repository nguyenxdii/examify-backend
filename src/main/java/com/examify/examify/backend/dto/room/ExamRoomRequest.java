package com.examify.examify.backend.dto.room;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.Instant;

@Data
public class ExamRoomRequest {
    @NotBlank
    private String examId;
    @NotBlank
    private String name;
    @NotBlank
    private String mode; // "exam" | "practice"
    private int durationMinutes = 0;
    private Instant openAt;
    private Instant closeAt;
    private int maxAttempts = 0;
    private boolean showAnswersAfterSubmission;
    private boolean showScoreAfterSubmission;
    private boolean showSubmissionAfterSubmission;
    private boolean requireStudentList;
}
