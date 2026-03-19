package com.examify.examify.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "student_list")
public class StudentList {
    @Id
    private String id;
    private String roomId;
    private String studentId; // MSSV
    private String studentName;
}
