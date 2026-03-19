package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document(collection = "submission_answers")
public class SubmissionAnswer {
    @Id
    private String id;
    private String submissionId; // ref: submissions
    private String questionId;   // ref: questions
    private List<String> selectedAnswer;
    private boolean isCorrect;
    private String essayAnswer;
    private float aiScore;
    private String aiComment;
    private float finalScore;
    private boolean isManuallyGraded;
}
