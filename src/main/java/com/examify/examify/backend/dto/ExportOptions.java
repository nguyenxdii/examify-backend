package com.examify.examify.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportOptions {
    private int numVersions = 1;
    private boolean shuffleQuestions = true;
    private boolean shuffleAnswers = true;
    private List<String> formats = new ArrayList<>(List.of("pdf")); // "pdf", "docx"
    private boolean showExplanations = true;
    private String subject;
    private int duration;
    private String greeting;
}
