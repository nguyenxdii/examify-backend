package com.examify.examify.backend.controller;

import com.examify.examify.backend.dto.room.*;
import com.examify.examify.backend.service.ExamRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ExamRoomController {

    private final ExamRoomService examRoomService;

    @PostMapping
    public ResponseEntity<ExamRoomResponse> createRoom(@Valid @RequestBody ExamRoomRequest request) {
        return ResponseEntity.ok(examRoomService.createRoom(request));
    }

    @GetMapping
    public ResponseEntity<List<ExamRoomResponse>> getMyRooms() {
        return ResponseEntity.ok(examRoomService.getMyRooms());
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ExamRoomResponse> getRoomDetail(@PathVariable String roomId) {
        return ResponseEntity.ok(examRoomService.getRoomDetail(roomId));
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<ExamRoomResponse> updateRoom(@PathVariable String roomId, @Valid @RequestBody ExamRoomRequest request) {
        return ResponseEntity.ok(examRoomService.updateRoom(roomId, request));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        examRoomService.deleteRoom(roomId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{roomId}/open")
    public ResponseEntity<Void> openRoom(@PathVariable String roomId) {
        examRoomService.openRoom(roomId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{roomId}/close")
    public ResponseEntity<Void> closeRoom(@PathVariable String roomId) {
        examRoomService.closeRoom(roomId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/students")
    public ResponseEntity<List<StudentListResponse>> uploadStudentList(@PathVariable String roomId, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(examRoomService.uploadStudentList(roomId, file));
    }

    @GetMapping("/{roomId}/students")
    public ResponseEntity<List<StudentListResponse>> getStudentList(@PathVariable String roomId) {
        return ResponseEntity.ok(examRoomService.getStudentList(roomId));
    }

    @PostMapping("/{roomId}/students/manual")
    public ResponseEntity<StudentListResponse> addStudentManual(@PathVariable String roomId, @RequestBody StudentListRequest.StudentEntry student) {
        return ResponseEntity.ok(examRoomService.addStudentManual(roomId, student));
    }

    @DeleteMapping("/{roomId}/students/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable String roomId, @PathVariable String id) {
        examRoomService.deleteStudent(roomId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roomId}/students/{id}")
    public ResponseEntity<StudentListResponse> updateStudent(@PathVariable String roomId, @PathVariable String id, @RequestBody StudentListRequest.StudentEntry student) {
        return ResponseEntity.ok(examRoomService.updateStudentManual(roomId, id, student));
    }

    @GetMapping("/{roomId}/submissions")
    public ResponseEntity<List<SubmissionSummaryResponse>> getRoomSubmissions(@PathVariable String roomId) {
        return ResponseEntity.ok(examRoomService.getRoomSubmissions(roomId));
    }

    @GetMapping("/{roomId}/submissions/{submissionId}")
    public ResponseEntity<SubmissionDetailResponse> getSubmissionDetail(@PathVariable String roomId, @PathVariable String submissionId) {
        return ResponseEntity.ok(examRoomService.getSubmissionDetail(roomId, submissionId));
    }

    @PatchMapping("/{roomId}/submissions/{submissionId}/grade")
    public ResponseEntity<SubmissionDetailResponse> gradeEssay(
            @PathVariable String roomId,
            @PathVariable String submissionId,
            @RequestBody GradeEssayRequest request
    ) {
        return ResponseEntity.ok(examRoomService.gradeEssay(roomId, submissionId, request));
    }

    // ===== PUBLIC ENDPOINTS FOR STUDENTS =====

    @GetMapping("/{roomId}/public")
    public ResponseEntity<Map<String, Object>> getRoomPublic(@PathVariable String roomId) {
        return ResponseEntity.ok(examRoomService.getRoomPublic(roomId));
    }

    @PostMapping("/{roomId}/submit")
    public ResponseEntity<com.examify.examify.backend.model.Submission> submitRoom(
            @PathVariable String roomId,
            @Valid @RequestBody com.examify.examify.backend.dto.exam.SubmissionRequest request
    ) {
        return ResponseEntity.ok(examRoomService.submitRoom(roomId, request));
    }
}
