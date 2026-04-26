package com.examify.examify.backend.dto.room;

import com.examify.examify.backend.model.Question;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SubmissionDetailResponse {
    private String submissionId;
    private String studentId;
    private String studentName;
    private float score;
    private int totalQuestions;
    private int correctCount;
    private String gradingStatus;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private int attemptNumber;
    private int maxAttempts;
    private List<AnswerDetailResponse> answers;

    @Data
    public static class AnswerDetailResponse {
        private String submissionAnswerId;
        private String questionId;
        private String questionContent;
        private String questionType;
        private List<Question.Choice> choices;
        private List<String> correctAnswers;
        private List<String> selectedAnswer;
        private String essayAnswer;
        @JsonProperty("isCorrect")
        private boolean isCorrect;
        private String explanation;
        private String sampleAnswer;
        private float aiScore;
        private String aiComment;
        private float finalScore;
        private boolean isManuallyGraded;
    }
}
