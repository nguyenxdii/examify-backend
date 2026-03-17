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
    private final ExamRoomRepository examRoomRepository;
    private final SubmissionRepository submissionRepository;

    private String getCurrentTeacherId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }
        throw new RuntimeException("Principal is not of type User: " + (principal != null ? principal.getClass().getName() : "null"));
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
        Exam exam = getExamAndCheckOwner(examId);
        String status = exam.getStatus();

        if ("ready".equals(status)) {
            boolean hasOpenRoom = examRoomRepository.existsByExamIdAndStatus(examId, "open");
            if (hasOpenRoom) {
                throw new RuntimeException("Không thể xóa đề thi khi có phòng thi đang mở");
            }
        } else if ("shared".equals(status)) {
            // Kiểm tra submissions thông qua các phòng thi của đề thi này
            List<ExamRoom> rooms = examRoomRepository.findByExamId(examId);
            for (ExamRoom room : rooms) {
                if (submissionRepository.existsByRoomId(room.getId())) {
                    throw new RuntimeException("Không thể xóa đề thi đã có học sinh nộp bài");
                }
            }
        }

        // Nếu là draft hoặc các điều kiện trên thỏa mãn (cho phép xóa)
        questionRepository.deleteByExamId(examId);
        examRepository.deleteById(examId);
    }

    // ===== QUESTION CRUD =====

    public Question addQuestion(String examId, QuestionRequest req) {
        Exam exam = getExamAndCheckOwner(examId);
        if (!"draft".equals(exam.getStatus())) {
            throw new RuntimeException("Không thể chỉnh sửa đề thi đã phát hành");
        }
        Question q = buildQuestion(examId, req);
        updateExamStatus(examId);
        return questionRepository.save(q);
    }

    public List<Question> getQuestions(String examId) {
        getExamAndCheckOwner(examId);
        return questionRepository.findByExamIdOrderByOrderIndex(examId);
    }

    public Question updateQuestion(String examId, String questionId, QuestionRequest req) {
        Exam exam = getExamAndCheckOwner(examId);
        if (!"draft".equals(exam.getStatus())) {
            throw new RuntimeException("Không thể chỉnh sửa đề thi đã phát hành");
        }
        Question existing = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu hỏi"));
        
        existing.setContent(req.getContent());
        existing.setType(req.getType());
        existing.setChoices(req.getChoices());
        existing.setCorrectAnswers(req.getCorrectAnswers());
        existing.setSampleAnswer(req.getSampleAnswer());
        existing.setScoringCriteria(req.getScoringCriteria());
        existing.setExplanation(req.getExplanation());
        existing.setDifficulty(req.getDifficulty());
        existing.setTopic(req.getTopic());
        existing.setTags(req.getTags());
        existing.setOrderIndex(req.getOrderIndex());
        existing.setUpdatedAt(LocalDateTime.now());
        return questionRepository.save(existing);
    }

    public void deleteQuestion(String examId, String questionId) {
        Exam exam = getExamAndCheckOwner(examId);
        if (!"draft".equals(exam.getStatus())) {
            throw new RuntimeException("Không thể chỉnh sửa đề thi đã phát hành");
        }
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
        q.setSampleAnswer(req.getSampleAnswer());
        q.setScoringCriteria(req.getScoringCriteria());
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
        // Chỉ tự động cập nhật sang ready nếu đang là draft
        if ("draft".equals(exam.getStatus()) && count > 0) {
            exam.setStatus("ready");
        } else if ("ready".equals(exam.getStatus()) && count == 0) {
            exam.setStatus("draft");
        }
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
