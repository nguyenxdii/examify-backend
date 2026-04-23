package com.examify.examify.backend.dto.exam;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExamRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;
    private String description;
    @NotBlank(message = "Môn học không được để trống")
    private String subject;
    private String status = "draft";
}
