package com.examify.examify.backend.dto.exam;

import com.examify.examify.backend.model.Question;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class QuestionRequest {
    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;
    private String type = "multiple_choice";
    private List<Question.Choice> choices;
    private List<String> correctAnswers;
    private String sampleAnswer;
    private String scoringCriteria;
    private String explanation;
    private String difficulty = "medium";
    private String topic;
    private List<String> tags;
    private int orderIndex;
}
