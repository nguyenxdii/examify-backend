package com.examify.examify.backend.dto.room;

import lombok.Data;

@Data
public class StudentListResponse {
    private String id;
    private String roomId;
    private String studentId;
    private String studentName;
    private boolean hasSubmitted;
}
