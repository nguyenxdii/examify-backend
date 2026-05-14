package com.examify.examify.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String email;
    private String fullName;
    private String gender;
    private String role;
    private String school;
    private String field;
    private boolean locked;
    private LocalDateTime createdAt;
}
