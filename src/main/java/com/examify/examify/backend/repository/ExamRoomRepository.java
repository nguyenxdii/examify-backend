package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.ExamRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ExamRoomRepository extends MongoRepository<ExamRoom, String> {
    List<ExamRoom> findByExamId(String examId);
    boolean existsByExamIdAndStatus(String examId, String status);
}
