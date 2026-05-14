package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.Exam;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ExamRepository extends MongoRepository<Exam, String> {
    List<Exam> findByTeacherIdOrderByCreatedAtDesc(String teacherId);
    long countByCreatedAtBefore(java.time.LocalDateTime date);
    long countByTeacherId(String teacherId);
    List<Exam> findTop5ByTeacherIdOrderByCreatedAtDesc(String teacherId);
}
