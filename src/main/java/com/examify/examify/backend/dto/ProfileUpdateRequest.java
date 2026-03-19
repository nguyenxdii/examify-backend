package com.examify.examify.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ProfileUpdateRequest {
    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;
    
    private String gender;
    private String school;
    private String field;
}
