package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.QuestionBank;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface QuestionBankRepository extends MongoRepository<QuestionBank, String> {
    List<QuestionBank> findByTeacherId(String teacherId);
}
