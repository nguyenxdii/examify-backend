package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.RoomAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface RoomAttemptRepository extends MongoRepository<RoomAttempt, String> {
    Optional<RoomAttempt> findByRoomIdAndStudentId(String roomId, String studentId);
    // Nếu phòng không bắt buộc MSSV, ta tìm theo tên
    Optional<RoomAttempt> findByRoomIdAndStudentName(String roomId, String studentName);
    void deleteByRoomId(String roomId);
}
