package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.exam.*;
import com.examify.examify.backend.model.*;
import com.examify.examify.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;

    private String getCurrentTeacherId() {
        User user = (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return user.getId();
    }

    private Exam getExamAndCheckOwner(String examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đề thi"));
        if (!exam.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Bạn không có quyền truy cập đề thi này");
        }
        return exam;
    }

    // ===== EXAM CRUD =====

    public ExamResponse createExam(ExamRequest request) {
        Exam exam = new Exam();
        exam.setTeacherId(getCurrentTeacherId());
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setSubject(request.getSubject());
        exam.setStatus("draft");
        exam.setCreatedAt(LocalDateTime.now());
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        return toResponse(exam);
    }

    public List<ExamResponse> getMyExams() {
        return examRepository
                .findByTeacherIdOrderByCreatedAtDesc(getCurrentTeacherId())
                .stream().map(this::toResponse).toList();
    }

    public ExamResponse getExamDetail(String examId) {
        return toResponse(getExamAndCheckOwner(examId));
    }

    public ExamResponse updateExam(String examId, ExamRequest request) {
        Exam exam = getExamAndCheckOwner(examId);
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setSubject(request.getSubject());
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        return toResponse(exam);
    }

    public void deleteExam(String examId) {
        getExamAndCheckOwner(examId);
        questionRepository.deleteByExamId(examId);
        examRepository.deleteById(examId);
    }

    // ===== QUESTION CRUD =====

    public Question addQuestion(String examId, QuestionRequest req) {
        getExamAndCheckOwner(examId);
        Question q = buildQuestion(examId, req);
        updateExamStatus(examId);
        return questionRepository.save(q);
    }

    public List<Question> getQuestions(String examId) {
        getExamAndCheckOwner(examId);
        return questionRepository.findByExamIdOrderByOrderIndex(examId);
    }

    public Question updateQuestion(String examId, String questionId, QuestionRequest req) {
        getExamAndCheckOwner(examId);
        Question existing = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu hỏi"));
        existing.setContent(req.getContent());
        existing.setType(req.getType());
        existing.setChoices(req.getChoices());
        existing.setCorrectAnswers(req.getCorrectAnswers());
        existing.setExplanation(req.getExplanation());
        existing.setDifficulty(req.getDifficulty());
        existing.setTopic(req.getTopic());
        existing.setTags(req.getTags());
        existing.setOrderIndex(req.getOrderIndex());
        existing.setUpdatedAt(LocalDateTime.now());
        return questionRepository.save(existing);
    }

    public void deleteQuestion(String examId, String questionId) {
        getExamAndCheckOwner(examId);
        questionRepository.deleteById(questionId);
        updateExamStatus(examId);
    }

    // Lưu hàng loạt câu hỏi từ AI
    public List<Question> saveBatchQuestions(String examId, List<QuestionRequest> requests) {
        getExamAndCheckOwner(examId);
        List<Question> questions = requests.stream()
                .map(req -> buildQuestion(examId, req))
                .toList();
        List<Question> saved = questionRepository.saveAll(questions);
        updateExamStatus(examId);
        return saved;
    }

    // ===== HELPERS =====

    private Question buildQuestion(String examId, QuestionRequest req) {
        Question q = new Question();
        q.setExamId(examId);
        q.setContent(req.getContent());
        q.setType(req.getType());
        q.setChoices(req.getChoices());
        q.setCorrectAnswers(req.getCorrectAnswers());
        q.setExplanation(req.getExplanation());
        q.setDifficulty(req.getDifficulty());
        q.setTopic(req.getTopic());
        q.setTags(req.getTags());
        q.setOrderIndex(req.getOrderIndex());
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        return q;
    }

    private void updateExamStatus(String examId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        long count = questionRepository.countByExamId(examId);
        exam.setStatus(count > 0 ? "ready" : "draft");
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
    }

    private ExamResponse toResponse(Exam exam) {
        return new ExamResponse(
            exam.getId(), exam.getTitle(), exam.getDescription(),
            exam.getSubject(), exam.getStatus(),
            questionRepository.countByExamId(exam.getId()),
            exam.getCreatedAt(), exam.getUpdatedAt()
        );
    }
}
