package com.examify.examify.backend.dto.ai;

import lombok.Data;
import java.util.List;

@Data
public class AnalyzeResponse {
    private int suggestedTotal;
    private int suggestedMultipleChoice;
    private int suggestedMultipleAnswer;
    private int suggestedEssay;
    private int suggestedEasy;
    private int suggestedMedium;
    private int suggestedHard;
    private List<String> detectedTopics;
    private String summary;
    
    // Valiđation fields
    @com.fasterxml.jackson.annotation.JsonProperty("isSufficient")
    private boolean isSufficient;
    private String message;
    
    // Metadata suggestions
    private String suggestedTitle;
    private String suggestedDescription;

    public AnalyzeResponse(int suggestedTotal, int suggestedMultipleChoice, int suggestedMultipleAnswer, int suggestedEssay, List<String> detectedTopics, String summary) {
        this.suggestedTotal = suggestedTotal;
        this.suggestedMultipleChoice = suggestedMultipleChoice;
        this.suggestedMultipleAnswer = suggestedMultipleAnswer;
        this.suggestedEssay = suggestedEssay;
        this.detectedTopics = detectedTopics;
        this.summary = summary;
        this.isSufficient = true;
    }

    public AnalyzeResponse() {}
}
