package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "submissions")
public class Submission {
    @Id
    private String id;
    private String roomId; // optional, ref: exam_rooms
    private String examId; // ref: exams (for public shared quizzes)
    private String studentName;
    private String studentId;
    private float score;
    private int totalQuestions;
    private int correctCount;
    private String gradingStatus; // "auto_graded" | "pending_review" | "fully_graded"
    private List<Question> questionSnapshot;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
}
