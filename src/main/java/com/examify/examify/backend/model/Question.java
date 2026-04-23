package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "questions")
public class Question {
    @Id
    private String id;
    private String examId;
    private String teacherId;
    private String content;
    private String subject; // Môn học (Toán, Văn, Anh, IT...)
    private String type; // "multiple_choice" | "multiple_answer" | "essay"
    private List<Choice> choices;
    private List<String> correctAnswers;
    private String sampleAnswer;
    private String scoringCriteria;
    private String explanation;
    private String difficulty; // "easy" | "medium" | "hard"
    private String topic;
    private List<String> tags;
    private int orderIndex;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class Choice {
        private String key;     // "A", "B", "C", "D"
        private String content;
    }
}
