package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SubmissionRepository extends MongoRepository<Submission, String> {
    List<Submission> findByRoomId(String roomId);
    boolean existsByRoomId(String roomId);
    List<Submission> findByRoomIdAndStudentId(String roomId, String studentId);
    long countByRoomId(String roomId);
}
