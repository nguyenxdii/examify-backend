package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "question_bank")
public class QuestionBank {
    @Id
    private String id;
    private String teacherId;
    private String content;
    private String type; // "multiple_choice" | "multiple_answer" | "essay"
    private List<Question.Choice> choices;
    private List<String> correctAnswers;
    private String sampleAnswer;
    private String scoringCriteria;
    private String explanation;
    private String difficulty; // "easy" | "medium" | "hard"
    private String subject;
    private String topic;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
