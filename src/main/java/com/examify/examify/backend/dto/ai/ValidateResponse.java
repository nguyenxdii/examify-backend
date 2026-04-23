package com.examify.examify.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidateResponse {
    private boolean isValid;
    private String extractedTopic;
    private String reason;
    private String detectedLanguage;
}
