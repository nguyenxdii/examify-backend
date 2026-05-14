package com.examify.examify.backend.dto.exam;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SubmissionRequest {
    private String studentName;
    private String studentId; // Optional for public quiz
    private Map<String, List<String>> answers; // questionId -> list of selected choice keys
    private List<String> questionOrder; // ordered question IDs
    private Map<String, List<String>> choiceOrder; // questionId -> ordered choice keys
}
