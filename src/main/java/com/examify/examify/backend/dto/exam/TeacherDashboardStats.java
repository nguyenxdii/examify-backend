package com.examify.examify.backend.dto.exam;

import com.examify.examify.backend.model.Exam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDashboardStats {
    private long totalExams;
    private long totalStudents;
    private long totalQuestionsInBank;
    private List<ExamResponse> recentExams;
    private List<RecentSubmission> recentSubmissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentSubmission {
        private String id;
        private String studentName;
        private String studentId;
        private String examTitle;
        private String roomName;
        private float score;
        private LocalDateTime submittedAt;
    }
}
