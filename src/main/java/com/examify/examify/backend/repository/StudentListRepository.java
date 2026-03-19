package com.examify.examify.backend.repository;

import com.examify.examify.backend.model.StudentList;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface StudentListRepository extends MongoRepository<StudentList, String> {
    List<StudentList> findByRoomId(String roomId);
    Optional<StudentList> findByRoomIdAndStudentId(String roomId, String studentId);
    boolean existsByRoomIdAndStudentId(String roomId, String studentId);
    void deleteByRoomId(String roomId);
}
