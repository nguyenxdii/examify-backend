package com.examify.examify.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String id;
    private String email;
    private String fullName;
    private String role;
    private String gender;
    private String school;
    private String field;
}