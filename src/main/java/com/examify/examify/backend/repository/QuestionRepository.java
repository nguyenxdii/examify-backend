package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.Question;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface QuestionRepository extends MongoRepository<Question, String> {
    List<Question> findByExamIdOrderByOrderIndex(String examId);
    void deleteByExamId(String examId);
    long countByExamId(String examId);
}
