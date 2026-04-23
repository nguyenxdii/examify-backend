package com.examify.examify.backend.dto.ai;

import com.examify.examify.backend.dto.exam.QuestionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GenerateAiResponse {
    @JsonProperty("isValid")
    private boolean isValid;
    private String reason;
    private String suggestedTitle;
    private String suggestedTopic;
    private List<QuestionRequest> questions;
}
