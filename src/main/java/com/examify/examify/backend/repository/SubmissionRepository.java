package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SubmissionRepository extends MongoRepository<Submission, String> {
    List<Submission> findByRoomId(String roomId);

    boolean existsByRoomId(String roomId);

    List<Submission> findByRoomIdAndStudentId(String roomId, String studentId);

    List<Submission> findByRoomIdAndStudentName(String roomId, String studentName);

    boolean existsByRoomIdAndStudentIdAndSubmittedAtAfter(String roomId, String studentId,
            java.time.LocalDateTime submittedAt);

    boolean existsByRoomIdAndStudentNameAndSubmittedAtAfter(String roomId, String studentName,
            java.time.LocalDateTime submittedAt);

    long countByRoomIdAndStudentId(String roomId, String studentId);

    long countByRoomIdAndStudentName(String roomId, String studentName);

    long countByRoomId(String roomId);

    void deleteByRoomId(String roomId);
}
