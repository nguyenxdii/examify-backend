package com.examify.examify.backend.dto.room;

import lombok.Data;
import java.util.List;

@Data
public class StudentListRequest {
    private List<StudentEntry> students;

    @Data
    public static class StudentEntry {
        private String studentId;
        private String studentName;
    }
}
