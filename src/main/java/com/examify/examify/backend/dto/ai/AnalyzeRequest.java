package com.examify.examify.backend.dto.ai;

import lombok.Data;

@Data
public class AnalyzeRequest {
    private String content;     // text dán trực tiếp
    private String inputType;   // "text" | "topic"
    private String language;    // "vi" | "en"
    private boolean validateOnly;
}
