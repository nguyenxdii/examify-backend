package com.examify.examify.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiGreetingRequest {
    private String pathname;
    private String fullName;
    private String language;
}
