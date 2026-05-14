package com.examify.examify.backend.controller;

import com.examify.examify.backend.dto.UserDTO;
import com.examify.examify.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PatchMapping("/users/{userId}/toggle-lock")
    public ResponseEntity<UserDTO> toggleUserLock(@PathVariable String userId, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(adminService.toggleUserLock(userId, reason));
    }

    @GetMapping("/exams")
    public ResponseEntity<List<com.examify.examify.backend.dto.AdminExamDTO>> getAllExams() {
        return ResponseEntity.ok(adminService.getAllExams());
    }

    @DeleteMapping("/exams/{examId}")
    public ResponseEntity<Void> deleteExam(@PathVariable String examId, @RequestParam String reason) {
        adminService.deleteExamByAdmin(examId, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<com.examify.examify.backend.dto.AdminStatsDTO> getStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    @GetMapping("/exams/{examId}")
    public ResponseEntity<com.examify.examify.backend.dto.AdminExamDetailDTO> getExamDetail(@PathVariable String examId) {
        return ResponseEntity.ok(adminService.getExamDetail(examId));
    }

    @GetMapping("/users/{userId}/detail")
    public ResponseEntity<com.examify.examify.backend.dto.AdminUserDetailDTO> getUserDetail(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.getUserDetail(userId));
    }
}
