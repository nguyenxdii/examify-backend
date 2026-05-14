package com.examify.examify.backend.dto;

import com.examify.examify.backend.model.Question;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AdminExamDetailDTO {
    private String id;
    private String title;
    private String description;
    private String subject;
    private String teacherName;
    private String teacherEmail;
    private int duration;
    private int passScore;
    private String status;
    private List<Question> questions;
    private Instant createdAt;
    private Instant updatedAt;
}
