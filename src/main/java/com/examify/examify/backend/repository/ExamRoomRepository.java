package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.ExamRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ExamRoomRepository extends MongoRepository<ExamRoom, String> {
    List<ExamRoom> findByTeacherIdOrderByCreatedAtDesc(String teacherId);
    Optional<ExamRoom> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    List<ExamRoom> findByExamId(String examId);
    boolean existsByExamIdAndStatus(String examId, String status);
}
