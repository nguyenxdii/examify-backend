package com.examify.examify.backend.dto.ai;

import lombok.Data;

@Data
public class GenerateRequest {
    private String examId;
    private String content;       // nội dung gốc
    private String inputType;     // "text" | "topic"
    private int multipleChoice;   // số câu trắc nghiệm 1 đáp án
    private int multipleAnswer;   // số câu trắc nghiệm nhiều đáp án
    private int essay;            // số câu tự luận
    private String difficulty;    // "easy" | "medium" | "hard" | "mixed"
    private int easyCount;
    private int mediumCount;
    private int hardCount;
    private String language;      // "vi" | "en"
    private boolean detailedExplanation;
}
