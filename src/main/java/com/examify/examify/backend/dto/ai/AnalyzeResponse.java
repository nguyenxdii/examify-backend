package com.examify.examify.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class AnalyzeResponse {
    private int suggestedTotal;
    private int suggestedMultipleChoice;
    private int suggestedMultipleAnswer;
    private int suggestedEssay;
    private List<String> detectedTopics;
    private String summary;
}
