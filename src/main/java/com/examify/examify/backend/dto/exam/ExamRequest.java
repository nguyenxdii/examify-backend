package com.examify.examify.backend.dto.exam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExamRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;
    private String description;
    @NotBlank(message = "Môn học không được để trống")
    private String subject;
    private String status = "draft";
    private Integer duration;
    private Integer passScore;
    private Boolean isShuffled;
}
