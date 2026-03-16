package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)  // email không được trùng
    private String email;

    private String passwordHash;
    private String fullName;
    private String role;        // "admin" | "teacher"
    private String school;
    private String field;
    private boolean isLocked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}