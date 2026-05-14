package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.room.*;
import com.examify.examify.backend.model.*;
import com.examify.examify.backend.repository.*;
import com.examify.examify.backend.dto.exam.SubmissionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Random;
import java.util.ArrayList;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
@RequiredArgsConstructor
public class ExamRoomService {

    private final ExamRoomRepository examRoomRepository;
    private final ExamRepository examRepository;
    private final StudentListRepository studentListRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final QuestionRepository questionRepository;
    private final RoomAttemptRepository roomAttemptRepository;
    private final GeminiService geminiService;
    private final AsyncGradingService asyncGradingService;

    private String getCurrentTeacherId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof User) {
                return ((User) principal).getId();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (examRoomRepository.existsByRoomCode(code));
        return code;
    }

    private void autoUpdateStatus(ExamRoom room) {
        Instant now = Instant.now();
        if ("closed".equals(room.getStatus()))
            return;

        if (room.getCloseAt() != null && now.isAfter(room.getCloseAt())) {
            room.setStatus("closed");
            examRoomRepository.save(room);
        } else if (room.getOpenAt() != null && now.isAfter(room.getOpenAt()) && "pending".equals(room.getStatus())) {
            room.setStatus("open");
            examRoomRepository.save(room);
        }
    }

    public ExamRoomResponse createRoom(ExamRoomRequest request) {
        Exam exam = examRepository.findById(request.getExamId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đề thi"));

        if (!exam.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Bạn không có quyền sử dụng đề thi này");
        }

        ExamRoom room = new ExamRoom();
        room.setExamId(request.getExamId());
        room.setTeacherId(getCurrentTeacherId());
        room.setName(request.getName());
        room.setMode(request.getMode());
        room.setRoomCode(generateRoomCode());
        room.setDurationMinutes(request.getDurationMinutes());
        room.setOpenAt(request.getOpenAt());
        room.setCloseAt(request.getCloseAt());
        room.setCreatedAt(Instant.now());
        room.setUpdatedAt(Instant.now());

        if ("practice".equals(request.getMode())) {
            room.setMaxAttempts(0);
            room.setShowAnswersAfterSubmission(true);
            room.setShowScoreAfterSubmission(true);
            room.setShowSubmissionAfterSubmission(true);
            room.setRequireStudentList(false);
        } else {
            room.setMaxAttempts(request.getMaxAttempts());
            room.setShowAnswersAfterSubmission(request.isShowAnswersAfterSubmission());
            room.setShowScoreAfterSubmission(request.isShowScoreAfterSubmission());
            room.setShowSubmissionAfterSubmission(request.isShowSubmissionAfterSubmission());
            room.setRequireStudentList(request.isRequireStudentList());
        }
        room.setScoresPublished(false); // Default to false
        room.setStatus("pending");

        Instant now = Instant.now();
        if (room.getOpenAt() != null && room.getOpenAt().isAfter(now)) {
            room.setStatus("pending");
        } else {
            room.setStatus("open");
        }

        examRoomRepository.save(room);
        return toResponse(room);
    }

    public List<ExamRoomResponse> getMyRooms() {
        List<ExamRoom> rooms = examRoomRepository.findByTeacherIdOrderByCreatedAtDesc(getCurrentTeacherId());
        return rooms.stream().map(room -> {
            autoUpdateStatus(room);
            return toResponse(room);
        }).toList();
    }

    public ExamRoomResponse getRoomDetail(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Bạn không có quyền truy cập phòng thi này");
        }

        autoUpdateStatus(room);
        return toResponse(room);
    }

    public ExamRoomResponse updateRoom(String roomId, ExamRoomRequest request) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa phòng thi này");
        }

        if (!"pending".equals(room.getStatus())) {
            throw new RuntimeException("Không thể chỉnh sửa phòng đã mở hoặc đã đóng");
        }

        room.setName(request.getName());
        room.setMode(request.getMode());
        room.setDurationMinutes(request.getDurationMinutes());
        room.setOpenAt(request.getOpenAt());
        room.setCloseAt(request.getCloseAt());
        room.setUpdatedAt(Instant.now());

        if ("practice".equals(request.getMode())) {
            room.setMaxAttempts(0);
            room.setShowAnswersAfterSubmission(true);
            room.setShowScoreAfterSubmission(true);
            room.setShowSubmissionAfterSubmission(true);
            room.setRequireStudentList(false);
        } else {
            room.setMaxAttempts(request.getMaxAttempts());
            room.setShowAnswersAfterSubmission(request.isShowAnswersAfterSubmission());
            room.setShowScoreAfterSubmission(request.isShowScoreAfterSubmission());
            room.setShowSubmissionAfterSubmission(request.isShowSubmissionAfterSubmission());
            room.setRequireStudentList(request.isRequireStudentList());
        }

        examRoomRepository.save(room);
        return toResponse(room);
    }

    public void openRoom(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        if ("closed".equals(room.getStatus())) {
            throw new RuntimeException("Không thể mở lại phòng đã đóng");
        }

        room.setStatus("open");
        room.setUpdatedAt(Instant.now());
        examRoomRepository.save(room);
    }

    public void closeRoom(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        room.setStatus("closed");
        room.setUpdatedAt(Instant.now());
        examRoomRepository.save(room);
    }

    public void deleteRoom(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        // 1. Xóa chi tiết câu trả lời của tất cả bài nộp trong phòng
        List<Submission> submissions = submissionRepository.findByRoomId(roomId);
        for (Submission s : submissions) {
            submissionAnswerRepository.deleteBySubmissionId(s.getId());
        }

        // 2. Xóa các bảng liên quan theo roomId
        submissionRepository.deleteByRoomId(roomId);
        roomAttemptRepository.deleteByRoomId(roomId);
        studentListRepository.deleteByRoomId(roomId);

        // 3. Xóa phòng thi
        examRoomRepository.delete(room);
    }

    // ===== STUDENT LIST MANAGEMENT =====

    public List<StudentListResponse> uploadStudentList(String roomId, MultipartFile file) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        List<StudentList> students = parseFile(file, roomId);
        if (students.isEmpty()) {
            throw new RuntimeException("Không tìm thấy dữ liệu học sinh hợp lệ trong file");
        }

        studentListRepository.deleteByRoomId(roomId);
        studentListRepository.saveAll(students);
        return getStudentList(roomId);
    }

    public List<StudentListResponse> previewStudentList(String roomId, MultipartFile file) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        List<StudentList> students = parseFile(file, roomId);
        return students.stream().map(s -> {
            StudentListResponse resp = new StudentListResponse();
            resp.setStudentId(s.getStudentId());
            resp.setStudentName(s.getStudentName());
            resp.setRoomId(roomId);
            return resp;
        }).toList();
    }

    private List<StudentList> parseFile(MultipartFile file, String roomId) {
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (filename.endsWith(".csv") || filename.endsWith(".txt")) {
                return parseCsv(file, roomId);
            } else {
                return parseExcel(file, roomId);
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("unsupported file type")) {
                throw new RuntimeException("Định dạng file không được hỗ trợ. Vui lòng sử dụng Excel (.xlsx, .xls) hoặc CSV.");
            }
            throw new RuntimeException("Lỗi xử lý file: " + e.getMessage());
        }
    }

    private List<StudentList> parseCsv(MultipartFile file, String roomId) throws Exception {
        List<StudentList> students = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int idCol = -1;
            int nameCol = -1;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",|;|\\t");
                if (idCol == -1 || nameCol == -1) {
                    for (int i = 0; i < parts.length; i++) {
                        String p = parts[i].trim().toLowerCase();
                        if (p.contains("mã số") || p.contains("mssv") || p.contains("id")) idCol = i;
                        if (p.contains("họ tên") || p.contains("họ và tên") || p.contains("name")) nameCol = i;
                    }
                    continue;
                }
                
                if (idCol < parts.length && nameCol < parts.length) {
                    String id = parts[idCol].trim();
                    String name = parts[nameCol].trim();
                    if (!id.isEmpty()) {
                        StudentList s = new StudentList();
                        s.setRoomId(roomId);
                        s.setStudentId(id);
                        s.setStudentName(name);
                        students.add(s);
                    }
                }
            }
        }
        return students;
    }



    private List<StudentList> parseExcel(MultipartFile file, String roomId) throws Exception {
        List<StudentList> students = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int idCol = -1;
            int nameCol = -1;

            for (Row row : sheet) {
                if (idCol == -1 || nameCol == -1) {
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        String cellVal = getCellValueAsString(row.getCell(i));
                        if (cellVal == null)
                            continue;
                        cellVal = cellVal.toLowerCase();
                        if (cellVal.contains("mã số") || cellVal.contains("mssv") || cellVal.contains("id"))
                            idCol = i;
                        if (cellVal.contains("họ và tên") || cellVal.contains("họ tên") || cellVal.contains("name"))
                            nameCol = i;
                    }
                    if (idCol != -1 && nameCol != -1)
                        continue;
                }

                if (idCol != -1 && nameCol != -1) {
                    String sid = getCellValueAsString(row.getCell(idCol));
                    String name = getCellValueAsString(row.getCell(nameCol));
                    if (sid != null && !sid.trim().isEmpty() && !sid.toLowerCase().contains("mssv")
                            && !sid.toLowerCase().contains("mã số")) {
                        StudentList student = new StudentList();
                        student.setRoomId(roomId);
                        student.setStudentId(sid.trim());
                        student.setStudentName(name != null ? name.trim() : "");
                        students.add(student);
                    }
                } else {
                    String sid = getCellValueAsString(row.getCell(0));
                    String name = getCellValueAsString(row.getCell(1));
                    if (sid != null && sid.matches("\\d+") && name != null && name.length() > 2) {
                        StudentList student = new StudentList();
                        student.setRoomId(roomId);
                        student.setStudentId(sid.trim());
                        student.setStudentName(name.trim());
                        students.add(student);
                    }
                }
            }
        }
        return students;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell))
                    return cell.getDateCellValue().toString();
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    public List<StudentListResponse> getStudentList(String roomId) {
        List<StudentList> list = studentListRepository.findByRoomId(roomId);
        return list.stream().map(s -> toStudentResponse(s, roomId)).toList();
    }

    public StudentListResponse addStudentManual(String roomId, StudentListRequest.StudentEntry entry) {
        if (studentListRepository.existsByRoomIdAndStudentId(roomId, entry.getStudentId())) {
            throw new RuntimeException("Mã số học sinh này đã tồn tại");
        }
        StudentList student = new StudentList();
        student.setRoomId(roomId);
        student.setStudentId(entry.getStudentId());
        student.setStudentName(entry.getStudentName());
        return toStudentResponse(studentListRepository.save(student), roomId);
    }

    public StudentListResponse updateStudentManual(String roomId, String id, StudentListRequest.StudentEntry entry) {
        StudentList student = studentListRepository.findById(id).orElseThrow();
        if (!student.getStudentId().equals(entry.getStudentId()) &&
                studentListRepository.existsByRoomIdAndStudentId(roomId, entry.getStudentId())) {
            throw new RuntimeException("Mã số học sinh mới đã tồn tại");
        }
        student.setStudentId(entry.getStudentId());
        student.setStudentName(entry.getStudentName());
        return toStudentResponse(studentListRepository.save(student), roomId);
    }

    public void deleteStudent(String roomId, String id) {
        studentListRepository.deleteById(id);
    }

    private StudentListResponse toStudentResponse(StudentList s, String roomId) {
        StudentListResponse resp = new StudentListResponse();
        resp.setId(s.getId());
        resp.setRoomId(s.getRoomId());
        resp.setStudentId(s.getStudentId());
        resp.setStudentName(s.getStudentName());
        resp.setHasSubmitted(!submissionRepository.findByRoomIdAndStudentId(roomId, s.getStudentId()).isEmpty());
        return resp;
    }

    // ===== SUBMISSIONS & GRADING =====

    private SubmissionDetailResponse.AnswerDetailResponse mapToAnswerDetail(SubmissionAnswer ans, Question q) {
        SubmissionDetailResponse.AnswerDetailResponse detail = new SubmissionDetailResponse.AnswerDetailResponse();
        detail.setSubmissionAnswerId(ans.getId());
        detail.setQuestionId(ans.getQuestionId());
        
        if (q != null) {
            detail.setQuestionContent(q.getContent());
            detail.setQuestionType(q.getType());
            detail.setChoices(q.getChoices());
            detail.setCorrectAnswers(q.getCorrectAnswers());
            detail.setExplanation(q.getExplanation());
            detail.setSampleAnswer(q.getSampleAnswer());
        }
        
        detail.setSelectedAnswer(ans.getSelectedAnswer());
        detail.setEssayAnswer(ans.getEssayAnswer());
        detail.setCorrect(ans.isCorrect());
        detail.setAiScore(ans.getAiScore());
        detail.setAiComment(ans.getAiComment());
        detail.setFinalScore(ans.getFinalScore());
        detail.setManuallyGraded(ans.isManuallyGraded());
        return detail;
    }

    public List<SubmissionSummaryResponse> getRoomSubmissions(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Bạn không có quyền truy cập");
        }

        List<Submission> submissions = submissionRepository.findByRoomId(roomId);

        // Group by student
        Map<String, List<Submission>> studentGroups = new HashMap<>();
        for (Submission s : submissions) {
            String key = (s.getStudentId() != null && !s.getStudentId().isEmpty()) ? s.getStudentId()
                    : s.getStudentName();
            studentGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<SubmissionSummaryResponse> result = new ArrayList<>();

        for (Map.Entry<String, List<Submission>> entry : studentGroups.entrySet()) {
            List<Submission> history = entry.getValue();
            history.sort((a, b) -> {
                if (a.getSubmittedAt() == null || b.getSubmittedAt() == null) return 0;
                return b.getSubmittedAt().compareTo(a.getSubmittedAt());
            });

            Submission latest = history.get(0);
            float avgScore = (float) history.stream().mapToDouble(Submission::getScore).average().orElse(0.0);

            SubmissionSummaryResponse resp = new SubmissionSummaryResponse();
            resp.setSubmissionId(latest.getId());
            resp.setStudentId(latest.getStudentId());
            resp.setStudentName(latest.getStudentName());
            resp.setScore(latest.getScore());
            resp.setTotalQuestions(latest.getTotalQuestions());
            resp.setCorrectCount(latest.getCorrectCount());
            resp.setGradingStatus(latest.getGradingStatus());
            resp.setSubmittedAt(latest.getSubmittedAt());
            resp.setHasPendingEssay("pending_essay".equals(latest.getGradingStatus()));
            resp.setAttemptNumber(history.size()); // The latest attempt number is the size
            resp.setTotalAttempts(history.size());
            resp.setMaxAttempts(room.getMaxAttempts());
            resp.setAvgScore(avgScore);
            resp.setPublished(latest.isPublished());
            resp.setGraded(latest.isGraded());
            result.add(resp);
        }

        result.sort((a, b) -> {
            if (a.getSubmittedAt() == null || b.getSubmittedAt() == null) return 0;
            return b.getSubmittedAt().compareTo(a.getSubmittedAt());
        });

        return result;
    }

    public SubmissionDetailResponse getSubmissionDetail(String roomId, String submissionId) {
        return getSubmissionDetail(roomId, submissionId, false);
    }

    public SubmissionDetailResponse getSubmissionDetail(String roomId, String submissionId, boolean forceStudentView) {
        Submission s = submissionRepository.findById(submissionId).orElseThrow();
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        Exam exam = examRepository.findById(room.getExamId()).orElseThrow();
        List<SubmissionAnswer> answers = submissionAnswerRepository.findBySubmissionId(submissionId);

        SubmissionDetailResponse resp = new SubmissionDetailResponse();
        resp.setSubmissionId(s.getId());
        resp.setRoomId(room.getId());
        resp.setRoomName(room.getName());
        resp.setStudentId(s.getStudentId());
        resp.setStudentName(s.getStudentName());
        resp.setExamTitle(exam.getTitle());

        resp.setScore(s.getScore());
        resp.setTotalQuestions(s.getTotalQuestions());
        resp.setCorrectCount(s.getCorrectCount());
        resp.setGradingStatus(s.getGradingStatus());
        resp.setStartedAt(s.getStartedAt());
        resp.setSubmittedAt(s.getSubmittedAt());
        resp.setMaxAttempts(room.getMaxAttempts());

        // Calculate attempt number for this specific submission and get all attempts
        List<Submission> studentHistory = (s.getStudentId() != null && !s.getStudentId().isEmpty())
                ? submissionRepository.findByRoomIdAndStudentId(roomId, s.getStudentId())
                : submissionRepository.findByRoomIdAndStudentName(roomId, s.getStudentName());

        studentHistory.sort((a, b) -> {
            if (a.getSubmittedAt() == null || b.getSubmittedAt() == null) return 0;
            return a.getSubmittedAt().compareTo(b.getSubmittedAt());
        });

        resp.setAllAttemptIds(studentHistory.stream().map(Submission::getId).toList());
        resp.setPublished(room.isScoresPublished() || s.isPublished());
        resp.setGraded(s.isGraded());

        for (int i = 0; i < studentHistory.size(); i++) {
            if (studentHistory.get(i).getId().equals(s.getId())) {
                resp.setAttemptNumber(i + 1);
                break;
            }
        }

        List<Question> snapshot = s.getQuestionSnapshot();
        List<SubmissionDetailResponse.AnswerDetailResponse> sortedAnswers = new ArrayList<>();

        if (snapshot != null && !snapshot.isEmpty()) {
            // SORT ANSWERS based on Snapshot order
            for (Question qSnap : snapshot) {
                Optional<SubmissionAnswer> ansOpt = answers.stream()
                        .filter(a -> a.getQuestionId().equals(qSnap.getId()))
                        .findFirst();
                
                if (ansOpt.isPresent()) {
                    sortedAnswers.add(mapToAnswerDetail(ansOpt.get(), qSnap));
                }
            }
        } else {
            // FALLBACK: Just return all answers in current order
            List<Question> examQuestions = questionRepository.findByExamId(exam.getId());
            for (SubmissionAnswer ans : answers) {
                Question q = examQuestions.stream()
                        .filter(eq -> eq.getId().equals(ans.getQuestionId()))
                        .findFirst().orElse(null);
                sortedAnswers.add(mapToAnswerDetail(ans, q));
            }
        }
        resp.setAnswers(sortedAnswers);

        // APPLY PRIVACY LOGIC FOR STUDENTS
        String currentTeacherId = getCurrentTeacherId();
        boolean isTeacher = !forceStudentView && currentTeacherId != null && currentTeacherId.equals(room.getTeacherId());
        
        if (!isTeacher) {
            boolean isPublished = room.isScoresPublished() || s.isPublished();
            
            // Flags based on room configuration
            // Publishing ONLY affects the visibility of the score/result at all
            // but WHAT is shown (answers, etc) is strictly controlled by toggles
            boolean canSeeAtAll = isPublished || room.isShowScoreAfterSubmission();
            
            boolean showScore = canSeeAtAll;
            boolean showSubmission = room.isShowSubmissionAfterSubmission() || room.isShowAnswersAfterSubmission();
            boolean showCorrectAnswers = room.isShowAnswersAfterSubmission();

            // Set visibility flags in response
            resp.setShowSubmission(showSubmission);
            resp.setShowAnswers(showCorrectAnswers);
            resp.setShowScoreAfterSubmission(room.isShowScoreAfterSubmission()); // Ensure frontend knows this flag

            if (!showScore) {
                // If score is hidden, we hide points and correct counts
                resp.setScore(0);
                resp.setCorrectCount(0);
                // ONLY set pending_announcement if not showScoreAfterSubmission
                if (!room.isShowScoreAfterSubmission()) {
                    resp.setGradingStatus("pending_announcement");
                }
            }
            
            if (!showSubmission) {
                // If submission is hidden, we don't return the answers list at all
                resp.setAnswers(new ArrayList<>());
            } else {
                // If can see submission, check if can see correct answers/explanations
                if (!showCorrectAnswers) {
                    resp.setAnswers(resp.getAnswers().stream().map(ans -> {
                        ans.setCorrectAnswers(null);
                        ans.setExplanation(null);
                        ans.setSampleAnswer(null);
                        ans.setCorrect(false); // Hide whether the student was right/wrong
                        ans.setAiComment(null);
                        ans.setAiScore(0);
                        ans.setFinalScore(0);
                        ans.setManuallyGraded(false); // Hide grading details
                        return ans;
                    }).toList());
                }

                // If teacher disabled seeing own choices but enabled seeing answers
                if (!room.isShowSubmissionAfterSubmission() && !isPublished) {
                    resp.setAnswers(resp.getAnswers().stream().map(ans -> {
                        ans.setSelectedAnswer(null);
                        ans.setEssayAnswer(null);
                        return ans;
                    }).toList());
                }
            }
        } else {
            // Teachers always see everything
            resp.setShowSubmission(true);
            resp.setShowAnswers(true);
        }
        return resp;
    }

    public SubmissionDetailResponse gradeEssay(String roomId, String submissionId, GradeEssayRequest req) {
        SubmissionAnswer answer = submissionAnswerRepository.findById(req.getSubmissionAnswerId()).orElseThrow();
        Submission s = submissionRepository.findById(submissionId).orElseThrow();
        float pointShare = s.getTotalQuestions() == 0 ? 0 : 10.0f / s.getTotalQuestions();

        answer.setFinalScore(req.getFinalScore() > 0 ? pointShare : 0);
        answer.setCorrect(req.getFinalScore() > 0);
        answer.setManuallyGraded(true);
        submissionAnswerRepository.save(answer);

        List<SubmissionAnswer> allAnswers = submissionAnswerRepository.findBySubmissionId(submissionId);
        long totalCorrect = allAnswers.stream().filter(SubmissionAnswer::isCorrect).count();
        float totalScore = s.getTotalQuestions() == 0 ? 0 : ((float) totalCorrect / s.getTotalQuestions()) * 10;

        s.setScore(totalScore);
        s.setCorrectCount((int) totalCorrect);
        if (req.isConfirm()) {
            boolean allEssayGraded = allAnswers.stream()
                    .filter(ans -> {
                        return s.getQuestionSnapshot().stream()
                                .anyMatch(q -> ans.getQuestionId().equals(q.getId()) && "essay".equals(q.getType()));
                    })
                    .allMatch(SubmissionAnswer::isManuallyGraded);

            if (allEssayGraded) {
                s.setGradingStatus("fully_graded");
                s.setGraded(true);
            }
        }
        submissionRepository.save(s);
        return getSubmissionDetail(roomId, submissionId);
    }

    // ===== STUDENT VALIDATION & SUBMISSION =====

    public Map<String, Object> validateStudent(String roomId, String studentId, String studentName, String roomCode) {
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        autoUpdateStatus(room);
        if (!"open".equals(room.getStatus()))
            throw new RuntimeException("Phòng thi hiện không mở");

        String sId = studentId != null ? studentId.trim() : null;
        String sName = studentName != null ? studentName.trim() : null;

        if (room.isRequireStudentList()) {
            if (sId == null || sId.isEmpty())
                throw new RuntimeException("Yêu cầu nhập MSSV");
            var entry = studentListRepository.findByRoomIdAndStudentId(roomId, sId)
                    .orElseThrow(() -> new RuntimeException("MSSV không có trong danh sách dự thi"));

            String regName = Normalizer.normalize(entry.getStudentName(), Normalizer.Form.NFC).trim().toLowerCase();
            String provName = Normalizer.normalize(sName, Normalizer.Form.NFC).trim().toLowerCase();
            if (!regName.equals(provName))
                throw new RuntimeException("Họ tên không khớp với MSSV đã đăng ký");
        }

        if (roomCode != null && !roomCode.trim().isEmpty()) {
            if (!room.getRoomCode().equalsIgnoreCase(roomCode))
                throw new RuntimeException("Mã phòng thi sai");
        } else {
            return Map.of("valid", true, "needsCode", true);
        }

        long submissionCount = (sId != null) 
            ? submissionRepository.countByRoomIdAndStudentId(roomId, sId)
            : submissionRepository.countByRoomIdAndStudentName(roomId, sName);

        if (room.getMaxAttempts() > 0) {
            if (submissionCount >= room.getMaxAttempts())
                throw new RuntimeException("Bạn đã hết lượt làm bài");
        }

        java.time.Instant now = java.time.Instant.now();
        java.util.Optional<RoomAttempt> existing = (sId != null)
                ? roomAttemptRepository.findByRoomIdAndStudentId(roomId, sId)
                : roomAttemptRepository.findByRoomIdAndStudentName(roomId, sName);

        RoomAttempt attempt;
        if (existing.isPresent()) {
            attempt = existing.get();
            
            // Logic: If they have already submitted a number of times EQUAL to the current attempt number,
            // or if the time has expired, we start a FRESH attempt timer.
            boolean isExpired = now.isAfter(attempt.getEndTime());
            boolean currentAttemptFinished = submissionCount >= attempt.getAttemptNumber();

            if (isExpired || currentAttemptFinished) {
                attempt.setStartTime(now);
                attempt.setEndTime(now.plus(room.getDurationMinutes(), ChronoUnit.MINUTES));
                attempt.setAttemptNumber((int) submissionCount + 1);
                attempt = roomAttemptRepository.save(attempt);
            }
        } else {
            attempt = new RoomAttempt();
            attempt.setRoomId(roomId);
            attempt.setStudentId(sId);
            attempt.setStudentName(sName);
            attempt.setStartTime(now);
            attempt.setEndTime(now.plus(room.getDurationMinutes(), ChronoUnit.MINUTES));
            attempt.setAttemptNumber(1);
            attempt = roomAttemptRepository.save(attempt);
        }

        return Map.of("valid", true, "roomName", room.getName(), "startTime", attempt.getStartTime(), "endTime",
                attempt.getEndTime());
    }

    public Map<String, Object> getRoomPublic(String roomId, String studentId, String studentName) {
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        autoUpdateStatus(room);
        if ("pending".equals(room.getStatus()))
            throw new RuntimeException("Phòng thi chưa mở");
        if ("closed".equals(room.getStatus()))
            throw new RuntimeException("Phòng thi đã đóng");

        Exam exam = examRepository.findById(room.getExamId()).orElseThrow();
        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(room.getExamId());
        
        if (exam.isShuffled()) {
            // Deterministic shuffle based on student and attempt
            long seedValue = (long) roomId.hashCode();
            int attemptNum = 1;

            if (studentId != null && !studentId.trim().isEmpty()) {
                seedValue += studentId.trim().hashCode();
                var attempt = roomAttemptRepository.findByRoomIdAndStudentId(roomId, studentId.trim());
                if (attempt.isPresent()) attemptNum = attempt.get().getAttemptNumber();
            } else if (studentName != null && !studentName.trim().isEmpty()) {
                seedValue += studentName.trim().hashCode();
                var attempt = roomAttemptRepository.findByRoomIdAndStudentName(roomId, studentName.trim());
                if (attempt.isPresent()) attemptNum = attempt.get().getAttemptNumber();
            }
            
            seedValue += attemptNum;
            Random rand = new Random(seedValue);
            
            // Create a copy to avoid modifying cache/database entities if they are shared
            questions = new ArrayList<>(questions);
            Collections.shuffle(questions, rand);
            
            for (Question q : questions) {
                if (q.getChoices() != null && !q.getChoices().isEmpty()) {
                    List<Question.Choice> shuffledChoices = new ArrayList<>(q.getChoices());
                    Collections.shuffle(shuffledChoices, rand);
                    q.setChoices(shuffledChoices);
                }
            }
        }

        // Security: Remove correct answers and explanations
        questions.forEach(q -> {
            q.setCorrectAnswers(null);
            q.setExplanation(null);
        });

        return Map.of(
            "room", toResponse(room), 
            "questions", questions, 
            "shuffled", exam.isShuffled(),
            "backendShuffled", true
        );
    }

    public com.examify.examify.backend.dto.room.SubmissionDetailResponse submitRoom(String roomId, SubmissionRequest request) {
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        autoUpdateStatus(room);
        if (!"open".equals(room.getStatus()))
            throw new RuntimeException("Phòng thi không mở");

        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(room.getExamId());
        boolean hasEssay = false;

        for (Question q : questions) {
            if ("essay".equals(q.getType())) {
                hasEssay = true;
                break;
            }
        }

        Submission s = new Submission();
        s.setRoomId(roomId);
        s.setExamId(room.getExamId());
        s.setStudentName(request.getStudentName() != null ? request.getStudentName().trim() : null);
        s.setStudentId(request.getStudentId() != null ? request.getStudentId().trim() : null);
        s.setGraded(false); // Default to not graded as per user request
        s.setSubmittedAt(Instant.now());
        s.setTotalQuestions(questions.size());
        
        // --- SHUFFLE SNAPSHOT LOGIC ---
        // Reorder questions and choices based on student's view if provided
        List<Question> snapshot = new ArrayList<>();
        if (request.getQuestionOrder() != null && !request.getQuestionOrder().isEmpty()) {
            Map<String, Question> qMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
            for (String qId : request.getQuestionOrder()) {
                Question originalQ = qMap.get(qId);
                if (originalQ != null) {
                    // Deep copy to avoid modifying original question repository cache if any
                    Question snapQ = new Question();
                    snapQ.setId(originalQ.getId());
                    snapQ.setContent(originalQ.getContent());
                    snapQ.setType(originalQ.getType());
                    snapQ.setCorrectAnswers(originalQ.getCorrectAnswers());
                    snapQ.setExplanation(originalQ.getExplanation());
                    snapQ.setSampleAnswer(originalQ.getSampleAnswer());
                    snapQ.setScoringCriteria(originalQ.getScoringCriteria());
                    
                    // Reorder choices
                    if (originalQ.getChoices() != null && request.getChoiceOrder() != null && request.getChoiceOrder().containsKey(qId)) {
                        List<String> cOrder = request.getChoiceOrder().get(qId);
                        Map<String, Question.Choice> cMap = originalQ.getChoices().stream()
                            .collect(Collectors.toMap(Question.Choice::getKey, c -> c));
                        List<Question.Choice> orderedChoices = new ArrayList<>();
                        for (String cKey : cOrder) {
                            if (cMap.containsKey(cKey)) orderedChoices.add(cMap.get(cKey));
                        }
                        snapQ.setChoices(orderedChoices);
                    } else {
                        snapQ.setChoices(originalQ.getChoices());
                    }
                    snapshot.add(snapQ);
                }
            }
        }
        
        // Fallback if no order provided or incomplete
        if (snapshot.size() < questions.size()) {
            snapshot = questions;
        }
        
        s.setQuestionSnapshot(snapshot); 
        
        Submission saved = submissionRepository.save(s);

        for (Question q : questions) {
            List<String> ans = request.getAnswers().get(q.getId());
            SubmissionAnswer sa = new SubmissionAnswer();
            sa.setSubmissionId(saved.getId());
            sa.setQuestionId(q.getId());
            sa.setSelectedAnswer(ans);
            if ("essay".equals(q.getType())) {
                String text = (ans != null && !ans.isEmpty()) ? ans.get(0) : "";
                sa.setEssayAnswer(text);
            } else {
                boolean correct = ans != null && new HashSet<>(ans)
                        .equals(new HashSet<>(q.getCorrectAnswers() != null ? q.getCorrectAnswers() : List.of()));
                sa.setCorrect(correct);
                float pointShare = questions.isEmpty() ? 0 : 10.0f / questions.size();
                sa.setFinalScore(correct ? pointShare : 0);
            }
            submissionAnswerRepository.save(sa);
        }

        // Finalize submission score on scale 10 (MC only for now)
        List<SubmissionAnswer> allSaved = submissionAnswerRepository.findBySubmissionId(saved.getId());
        long correctMC = allSaved.stream()
                .filter(sa -> sa.isCorrect() && sa.getEssayAnswer() == null)
                .count();

        float mcScore = (float) correctMC / questions.size() * 10;

        saved.setScore(mcScore);
        saved.setCorrectCount((int) correctMC);
        saved.setGraded(false); // Always false by default as per user request
        saved.setPublished(false);
        
        // Initial status is always pending review or pending essay
        if (!hasEssay) {
            saved.setGradingStatus("pending_confirmation");
        } else {
            saved.setGradingStatus("pending_essay");
            asyncGradingService.gradeSubmissionEssaysAsync(saved.getId(), questions);
        }

        submissionRepository.save(saved);
        return getSubmissionDetail(roomId, saved.getId());
    }

    public void publishScores(String roomId, boolean published) {
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Unauthorized");
        }
        room.setScoresPublished(published);
        examRoomRepository.save(room);

        // Update all submissions
        List<Submission> submissions = submissionRepository.findByRoomId(roomId);
        for (Submission s : submissions) {
            // If publishing globally, only publish those already graded
            // If unpublishing globally, unpublish everything
            if (published) {
                if (s.isGraded()) s.setPublished(true);
            } else {
                s.setPublished(false);
            }
        }
        submissionRepository.saveAll(submissions);
    }

    public void publishIndividualScore(String submissionId, boolean published) {
        Submission s = submissionRepository.findById(submissionId).orElseThrow();
        ExamRoom room = examRoomRepository.findById(s.getRoomId()).orElseThrow();
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Unauthorized");
        }
        s.setPublished(published);
        if (published) {
            s.setGraded(true);
            s.setGradingStatus("fully_graded");
        }
        submissionRepository.save(s);
    }

    public void toggleSubmissionGraded(String submissionId, boolean graded) {
        Submission s = submissionRepository.findById(submissionId).orElseThrow();
        ExamRoom room = examRoomRepository.findById(s.getRoomId()).orElseThrow();
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Unauthorized");
        }
        s.setGraded(graded);
        if (graded) {
            s.setGradingStatus("fully_graded");
        } else {
            // Revert status based on content
            List<Question> questions = questionRepository.findByExamId(room.getExamId());
            boolean hasEssay = questions.stream().anyMatch(q -> "essay".equals(q.getType()));
            s.setGradingStatus(hasEssay ? "pending_essay" : "pending_confirmation");
        }
        submissionRepository.save(s);
    }

    public List<SubmissionDetailResponse> lookupResult(String studentId, String roomCode) {
        ExamRoom room = examRoomRepository.findByRoomCode(roomCode.trim().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Mã phòng thi không chính xác"));

        List<Submission> submissions = submissionRepository.findByRoomIdAndStudentId(room.getId(), studentId.trim());
        if (submissions.isEmpty()) {
            throw new RuntimeException("Không tìm thấy kết quả cho MSSV này trong phòng thi");
        }

        return submissions.stream()
                .map(s -> getSubmissionDetail(room.getId(), s.getId(), true))
                .sorted((a, b) -> {
                    if (a.getSubmittedAt() == null || b.getSubmittedAt() == null) return 0;
                    return b.getSubmittedAt().compareTo(a.getSubmittedAt());
                })
                .toList();
    }

    private ExamRoomResponse toResponse(ExamRoom room) {
        Exam exam = examRepository.findById(room.getExamId()).orElse(null);
        String examTitle = exam != null ? exam.getTitle() : "N/A";

        return new ExamRoomResponse(
                room.getId(),
                room.getExamId(),
                examTitle,
                room.getName(),
                room.getMode(),
                room.getRoomCode(),
                room.getDurationMinutes(),
                room.getOpenAt(),
                room.getCloseAt(),
                room.getMaxAttempts(),
                room.isShowAnswersAfterSubmission(),
                room.isShowScoreAfterSubmission(),
                room.isShowSubmissionAfterSubmission(),
                room.isScoresPublished(),
                room.isRequireStudentList(),
                room.getStatus(),
                submissionRepository.countByRoomId(room.getId()),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }
}
