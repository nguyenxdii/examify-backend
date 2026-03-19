package com.examify.examify.backend.dto.room;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExamRoomRequest {
    @NotBlank
    private String examId;
    @NotBlank
    private String name;
    @NotBlank
    private String mode; // "exam" | "practice"
    private int durationMinutes = 0;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private int maxAttempts = 0;
    private boolean showAnswerAfter;
    private boolean requireStudentList;
}
