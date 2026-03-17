package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.SubmissionAnswer;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SubmissionAnswerRepository extends MongoRepository<SubmissionAnswer, String> {
    List<SubmissionAnswer> findBySubmissionId(String submissionId);
}
