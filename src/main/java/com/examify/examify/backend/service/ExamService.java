package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.exam.*;
import com.examify.examify.backend.model.*;
import com.examify.examify.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ExamRoomRepository examRoomRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final StudentListRepository studentListRepository;
    private final QuestionBankRepository questionBankRepository;
    private final GeminiService geminiService;

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
        
        // Map "published" from frontend to "ready" in backend if needed
        String status = request.getStatus();
        if ("published".equals(status)) status = "ready";
        exam.setStatus(status != null ? status : "draft");
        exam.setDuration(request.getDuration());
        exam.setPassScore(request.getPassScore());
        exam.setShuffled(request.getIsShuffled() != null ? request.getIsShuffled() : false);
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
        if (request.getStatus() != null) {
            String status = request.getStatus();
            if ("published".equals(status)) status = "ready";
            exam.setStatus(status);
        }
        if (request.getDuration() != null) {
            exam.setDuration(request.getDuration());
        }
        if (request.getPassScore() != null) {
            exam.setPassScore(request.getPassScore());
        }
        if (request.getIsShuffled() != null) {
            exam.setShuffled(request.getIsShuffled());
        }
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        return toResponse(exam);
    }

    public void deleteExam(String examId) {
        Exam exam = getExamAndCheckOwner(examId);
        // Kiểm tra xem đề thi có đang được sử dụng trong phòng thi nào không
        List<ExamRoom> rooms = examRoomRepository.findByExamId(examId);
        for (ExamRoom room : rooms) {
            if ("open".equals(room.getStatus())) {
                throw new RuntimeException("Không thể xóa đề thi khi có phòng thi đang mở");
            }
            if (submissionRepository.existsByRoomId(room.getId())) {
                throw new RuntimeException("Không thể xóa đề thi đã có học sinh làm bài");
            }
        }

        // Nếu cho phép xóa: Xóa dữ liệu liên quan
        for (ExamRoom room : rooms) {
            studentListRepository.deleteByRoomId(room.getId());
            examRoomRepository.deleteById(room.getId());
        }
        
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
        Question saved = questionRepository.save(q);
        
        if (req.isSaveToBank()) {
            saveToBank(saved);
        }
        
        updateExamStatus(examId);
        return saved;
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
        existing.setSubject(req.getSubject() != null ? req.getSubject() : exam.getSubject());
        existing.setTags(req.getTags());
        existing.setOrderIndex(req.getOrderIndex());
        existing.setUpdatedAt(LocalDateTime.now());
        Question saved = questionRepository.save(existing);

        if (req.isSaveToBank()) {
            saveToBank(saved);
        }

        return saved;
    }

    private void saveToBank(Question q) {
        QuestionBank bank = new QuestionBank();
        bank.setTeacherId(q.getTeacherId());
        bank.setContent(q.getContent());
        bank.setType(q.getType());
        bank.setChoices(q.getChoices());
        bank.setCorrectAnswers(q.getCorrectAnswers());
        bank.setSampleAnswer(q.getSampleAnswer());
        bank.setScoringCriteria(q.getScoringCriteria());
        bank.setExplanation(q.getExplanation());
        bank.setDifficulty(q.getDifficulty());
        bank.setSubject(q.getSubject());
        bank.setTopic(q.getTopic());
        bank.setTags(q.getTags());
        bank.setCreatedAt(LocalDateTime.now());
        bank.setUpdatedAt(LocalDateTime.now());
        questionBankRepository.save(bank);
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
                .map(req -> {
                    Question q = buildQuestion(examId, req);
                    Question saved = questionRepository.save(q);
                    if (req.isSaveToBank()) {
                        saveToBank(saved);
                    }
                    return saved;
                })
                .toList();
        updateExamStatus(examId);
        return questions;
    }

    public List<QuestionBank> getQuestionBank() {
        return questionBankRepository.findByTeacherId(getCurrentTeacherId());
    }

    // ===== HELPERS =====

    private Question buildQuestion(String examId, QuestionRequest req) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        Question q = new Question();
        q.setExamId(examId);
        q.setTeacherId(exam.getTeacherId());
        q.setContent(req.getContent());
        q.setSubject(req.getSubject() != null ? req.getSubject() : exam.getSubject());
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
        // Không tự động nâng cấp lên ready nữa, để người dùng quyết định
        if ("ready".equals(exam.getStatus()) && count == 0) {
            exam.setStatus("draft");
        }
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
    }

    private ExamResponse toResponse(Exam exam) {
        return new ExamResponse(
            exam.getId(), exam.getTitle(), exam.getDescription(),
            exam.getSubject(), exam.getStatus(),
            exam.getDuration(), exam.getPassScore(),
            exam.isShuffled(),
            questionRepository.countByExamId(exam.getId()),
            exam.getCreatedAt(), exam.getUpdatedAt()
        );
    }

    public Submission submitPublic(String examId, SubmissionRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đề thi"));
        
        if (!"shared".equals(exam.getStatus())) {
            throw new RuntimeException("Đề thi này chưa được chia sẻ công khai");
        }

        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(examId);
        int totalQuestions = questions.size();
        float totalEarnedScore = 0;
        int correctCount = 0;
        boolean hasEssay = false;

        for (Question q : questions) {
            List<String> studentAns = request.getAnswers().get(q.getId());
            
            if ("essay".equals(q.getType())) {
                hasEssay = true;
                String studentText = (studentAns != null && !studentAns.isEmpty()) ? studentAns.get(0) : "";
                if (!studentText.trim().isEmpty()) {
                    Map<String, Object> aiResult = geminiService.evaluateEssay(
                        q.getContent(), q.getSampleAnswer(), q.getScoringCriteria(), studentText
                    );
                    float score = ((Double) aiResult.get("score")).floatValue();
                    totalEarnedScore += score;
                    if (score >= 10.0f) correctCount++;
                }
            } else if (studentAns != null && q.getCorrectAnswers() != null) {
                // Trắc nghiệm
                Set<String> studentSet = new HashSet<>(studentAns);
                Set<String> correctSet = new HashSet<>(q.getCorrectAnswers());
                if (studentSet.equals(correctSet) && !studentSet.isEmpty()) {
                    totalEarnedScore += 10;
                    correctCount++;
                }
            }
        }

        float finalScore = totalQuestions > 0 ? (totalEarnedScore / totalQuestions) : 0;

        Submission submission = new Submission();
        submission.setExamId(examId);
        submission.setStudentName(request.getStudentName());
        submission.setStudentId(request.getStudentId());
        submission.setScore(finalScore);
        submission.setTotalQuestions(totalQuestions);
        submission.setCorrectCount(correctCount);
        submission.setGradingStatus(hasEssay ? "ai_graded_essay" : "auto_graded");
        submission.setStartedAt(LocalDateTime.now().minusMinutes(exam.getDuration() != null ? exam.getDuration() : 10)); 
        submission.setSubmittedAt(LocalDateTime.now());
        
        // Lưu snapshot câu hỏi để xem lại sau này
        submission.setQuestionSnapshot(new ArrayList<>(questions));
        
        Submission saved = submissionRepository.save(submission);

        // Lưu câu trả lời chi tiết
        for (Question q : questions) {
            List<String> ans = request.getAnswers().get(q.getId());
            if (ans != null) {
                SubmissionAnswer sa = new SubmissionAnswer();
                sa.setSubmissionId(saved.getId());
                sa.setQuestionId(q.getId());
                sa.setSelectedAnswer(ans);
                
                if ("essay".equals(q.getType())) {
                    String studentText = ans.get(0);
                    sa.setEssayAnswer(studentText);
                    
                    // Thực hiện chấm điểm lại để lấy feedback (hoặc cache từ vòng lặp trên)
                    // Ở đây tôi thực hiện lại để đơn giản hóa logic lưu trữ
                    if (!studentText.trim().isEmpty()) {
                        Map<String, Object> aiResult = geminiService.evaluateEssay(
                            q.getContent(), q.getSampleAnswer(), q.getScoringCriteria(), studentText
                        );
                        sa.setAiScore(((Double) aiResult.get("score")).floatValue());
                        sa.setAiComment((String) aiResult.get("feedback"));
                    }
                } else if (q.getCorrectAnswers() != null) {
                    Set<String> studentSet = new HashSet<>(ans);
                    Set<String> correctSet = new HashSet<>(q.getCorrectAnswers());
                    sa.setCorrect(studentSet.equals(correctSet) && !studentSet.isEmpty());
                }
                
                submissionAnswerRepository.save(sa);
            }
        }

        return saved;
    }
}
