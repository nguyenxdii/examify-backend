package com.examify.examify.backend.controller;

import com.examify.examify.backend.dto.exam.*;
import com.examify.examify.backend.model.Question;
import com.examify.examify.backend.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.examify.examify.backend.dto.ExportOptions;
import com.examify.examify.backend.service.ExamExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamExportService examExportService;

    @PostMapping
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody ExamRequest request) {
        return ResponseEntity.ok(examService.createExam(request));
    }

    @GetMapping
    public ResponseEntity<List<ExamResponse>> getMyExams() {
        return ResponseEntity.ok(examService.getMyExams());
    }

    @GetMapping("/{examId}")
    public ResponseEntity<ExamResponse> getExamDetail(@PathVariable String examId) {
        return ResponseEntity.ok(examService.getExamDetail(examId));
    }

    @PutMapping("/{examId}")
    public ResponseEntity<ExamResponse> updateExam(
            @PathVariable String examId,
            @Valid @RequestBody ExamRequest request) {
        return ResponseEntity.ok(examService.updateExam(examId, request));
    }

    @DeleteMapping("/{examId}")
    public ResponseEntity<Void> deleteExam(@PathVariable String examId) {
        examService.deleteExam(examId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{examId}/clone")
    public ResponseEntity<ExamResponse> cloneExam(@PathVariable String examId) {
        return ResponseEntity.ok(examService.cloneExam(examId));
    }

    @PostMapping("/{examId}/questions")
    public ResponseEntity<Question> addQuestion(
            @PathVariable String examId,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(examService.addQuestion(examId, request));
    }

    @GetMapping("/{examId}/questions")
    public ResponseEntity<List<Question>> getQuestions(@PathVariable String examId) {
        return ResponseEntity.ok(examService.getQuestions(examId));
    }

    @PutMapping("/{examId}/questions/{questionId}")
    public ResponseEntity<Question> updateQuestion(
            @PathVariable String examId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(examService.updateQuestion(examId, questionId, request));
    }

    @DeleteMapping("/{examId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable String examId,
            @PathVariable String questionId) {
        examService.deleteQuestion(examId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{examId}/questions/batch")
    public ResponseEntity<List<Question>> saveBatchQuestions(
            @PathVariable String examId,
            @RequestBody List<QuestionRequest> requests) {
        return ResponseEntity.ok(examService.saveBatchQuestions(examId, requests));
    }

    @GetMapping("/questions/bank")
    public ResponseEntity<List<com.examify.examify.backend.model.QuestionBank>> getQuestionBank() {
        return ResponseEntity.ok(examService.getQuestionBank());
    }

    @PostMapping("/questions/bank")
    public ResponseEntity<com.examify.examify.backend.model.QuestionBank> saveToBankStandalone(
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(examService.saveToBankStandalone(request));
    }

    @PutMapping("/questions/bank/{id}")
    public ResponseEntity<com.examify.examify.backend.model.QuestionBank> updateQuestionBank(
            @PathVariable String id,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(examService.updateQuestionBank(id, request));
    }

    @DeleteMapping("/questions/bank/{id}")
    public ResponseEntity<Void> deleteQuestionBank(@PathVariable String id) {
        examService.deleteQuestionBank(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/questions/topics")
    public ResponseEntity<List<String>> getQuestionBankTopics() {
        return ResponseEntity.ok(examService.getQuestionBankTopics());
    }

    @PostMapping("/{examId}/submit")
    public ResponseEntity<com.examify.examify.backend.model.Submission> submitPublic(
            @PathVariable String examId,
            @Valid @RequestBody SubmissionRequest request) {
        return ResponseEntity.ok(examService.submitPublic(examId, request));
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<TeacherDashboardStats> getTeacherStats() {
        return ResponseEntity.ok(examService.getTeacherStats());
    }

    @PostMapping("/{examId}/export")
    public ResponseEntity<byte[]> exportExam(@PathVariable String examId, @RequestBody ExportOptions options)
            throws IOException {
        byte[] zip = examExportService.exportExamToZip(examId, options);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exam_export.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }
}
