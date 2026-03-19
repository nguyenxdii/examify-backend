package com.examify.examify.backend.dto.room;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExamRoomResponse {
    private String id;
    private String examId;
    private String examTitle;
    private String name;
    private String mode;
    private String roomCode;
    private int durationMinutes;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private int maxAttempts;
    private boolean showAnswerAfter;
    private boolean requireStudentList;
    private String status;
    private long submissionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
