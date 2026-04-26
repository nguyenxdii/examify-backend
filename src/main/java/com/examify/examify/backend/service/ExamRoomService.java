package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.room.*;
import com.examify.examify.backend.model.*;
import com.examify.examify.backend.repository.*;
import com.examify.examify.backend.dto.exam.SubmissionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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

    private String getCurrentTeacherId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
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
        LocalDateTime now = LocalDateTime.now();
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
        room.setCreatedAt(LocalDateTime.now());
        room.setUpdatedAt(LocalDateTime.now());

        if ("practice".equals(request.getMode())) {
            room.setMaxAttempts(0);
            room.setShowAnswerAfter(true);
            room.setRequireStudentList(false);
        } else {
            room.setMaxAttempts(request.getMaxAttempts());
            room.setShowAnswerAfter(request.isShowAnswerAfter());
            room.setRequireStudentList(request.isRequireStudentList());
        }

        LocalDateTime now = LocalDateTime.now();
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
        room.setUpdatedAt(LocalDateTime.now());

        if ("practice".equals(request.getMode())) {
            room.setMaxAttempts(0);
            room.setShowAnswerAfter(true);
            room.setRequireStudentList(false);
        } else {
            room.setMaxAttempts(request.getMaxAttempts());
            room.setShowAnswerAfter(request.isShowAnswerAfter());
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
        room.setUpdatedAt(LocalDateTime.now());
        examRoomRepository.save(room);
    }

    public void closeRoom(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        room.setStatus("closed");
        room.setUpdatedAt(LocalDateTime.now());
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

        try {
            List<StudentList> students;
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

            if (filename.endsWith(".docx")) {
                students = parseWord(file, roomId);
            } else {
                students = parseExcel(file, roomId);
            }

            if (students.isEmpty()) {
                throw new RuntimeException("Không tìm thấy dữ liệu học sinh hợp lệ trong file");
            }

            studentListRepository.deleteByRoomId(roomId);
            studentListRepository.saveAll(students);
            return getStudentList(roomId);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý file: " + e.getMessage());
        }
    }

    private List<StudentList> parseWord(MultipartFile file, String roomId) throws Exception {
        List<StudentList> students = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFTable table : doc.getTables()) {
                int idCol = -1;
                int nameCol = -1;

                for (XWPFTableRow row : table.getRows()) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    if (idCol == -1 || nameCol == -1) {
                        for (int i = 0; i < cells.size(); i++) {
                            String text = cells.get(i).getText().toLowerCase();
                            if (text.contains("mã số") || text.contains("mssv") || text.contains("id"))
                                idCol = i;
                            if (text.contains("họ tên") || text.contains("họ và tên") || text.contains("name"))
                                nameCol = i;
                        }
                        if (idCol != -1 && nameCol != -1)
                            continue;
                    }

                    if (idCol != -1 && nameCol != -1 && idCol < cells.size() && nameCol < cells.size()) {
                        String sid = cells.get(idCol).getText().trim();
                        String name = cells.get(nameCol).getText().trim();
                        if (!sid.isEmpty() && !sid.equalsIgnoreCase("mssv") && !sid.equalsIgnoreCase("id")) {
                            StudentList s = new StudentList();
                            s.setRoomId(roomId);
                            s.setStudentId(sid);
                            s.setStudentName(name);
                            students.add(s);
                        }
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

    public List<SubmissionSummaryResponse> getRoomSubmissions(String roomId) {
        List<Submission> submissions = submissionRepository.findByRoomId(roomId);

        // Group by student to calculate attempt numbers
        Map<String, List<Submission>> studentSubmissions = new HashMap<>();
        for (Submission s : submissions) {
            String key = (s.getStudentId() != null && !s.getStudentId().isEmpty()) ? s.getStudentId()
                    : s.getStudentName();
            studentSubmissions.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        // Sort each student's submissions by date to assign attempt numbers
        for (List<Submission> list : studentSubmissions.values()) {
            list.sort(Comparator.comparing(Submission::getSubmittedAt));
        }

        submissions.sort((a, b) -> b.getSubmittedAt().compareTo(a.getSubmittedAt()));

        return submissions.stream().map(s -> {
            SubmissionSummaryResponse resp = new SubmissionSummaryResponse();
            resp.setSubmissionId(s.getId());
            resp.setStudentId(s.getStudentId());
            resp.setStudentName(s.getStudentName());
            resp.setScore(s.getScore());
            resp.setTotalQuestions(s.getTotalQuestions());
            resp.setCorrectCount(s.getCorrectCount());
            resp.setGradingStatus(s.getGradingStatus());
            resp.setSubmittedAt(s.getSubmittedAt());
            resp.setHasPendingEssay("pending_review".equals(s.getGradingStatus()));

            // Calculate attempt number
            String key = (s.getStudentId() != null && !s.getStudentId().isEmpty()) ? s.getStudentId()
                    : s.getStudentName();
            List<Submission> history = studentSubmissions.get(key);
            if (history != null) {
                resp.setAttemptNumber(history.indexOf(s) + 1);
            }

            return resp;
        }).toList();
    }

    public SubmissionDetailResponse getSubmissionDetail(String roomId, String submissionId) {
        Submission s = submissionRepository.findById(submissionId).orElseThrow();
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        List<SubmissionAnswer> answers = submissionAnswerRepository.findBySubmissionId(submissionId);

        SubmissionDetailResponse resp = new SubmissionDetailResponse();
        resp.setSubmissionId(s.getId());
        resp.setStudentId(s.getStudentId());
        resp.setStudentName(s.getStudentName());
        resp.setScore(s.getScore());
        resp.setTotalQuestions(s.getTotalQuestions());
        resp.setCorrectCount(s.getCorrectCount());
        resp.setGradingStatus(s.getGradingStatus());
        resp.setStartedAt(s.getStartedAt());
        resp.setSubmittedAt(s.getSubmittedAt());
        resp.setMaxAttempts(room.getMaxAttempts());

        // Calculate attempt number for this specific submission
        List<Submission> studentHistory = (s.getStudentId() != null && !s.getStudentId().isEmpty())
                ? submissionRepository.findByRoomIdAndStudentId(roomId, s.getStudentId())
                : submissionRepository.findByRoomIdAndStudentName(roomId, s.getStudentName());

        studentHistory.sort(Comparator.comparing(Submission::getSubmittedAt));
        for (int i = 0; i < studentHistory.size(); i++) {
            if (studentHistory.get(i).getId().equals(s.getId())) {
                resp.setAttemptNumber(i + 1);
                break;
            }
        }

        List<Question> snapshot = s.getQuestionSnapshot();
        resp.setAnswers(answers.stream().map(ans -> {
            SubmissionDetailResponse.AnswerDetailResponse detail = new SubmissionDetailResponse.AnswerDetailResponse();
            detail.setSubmissionAnswerId(ans.getId());
            detail.setQuestionId(ans.getQuestionId());

            Optional<Question> qOpt = snapshot.stream()
                    .filter(q -> ans.getQuestionId().equals(q.getId()))
                    .findFirst();

            if (qOpt.isPresent()) {
                Question q = qOpt.get();
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
        }).toList());
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

        if (room.isRequireStudentList()) {
            if (studentId == null || studentId.trim().isEmpty())
                throw new RuntimeException("Yêu cầu nhập MSSV");
            var entry = studentListRepository.findByRoomIdAndStudentId(roomId, studentId.trim())
                    .orElseThrow(() -> new RuntimeException("MSSV không có trong danh sách dự thi"));

            String regName = Normalizer.normalize(entry.getStudentName(), Normalizer.Form.NFC).trim().toLowerCase();
            String provName = Normalizer.normalize(studentName, Normalizer.Form.NFC).trim().toLowerCase();
            if (!regName.equals(provName))
                throw new RuntimeException("Họ tên không khớp với MSSV đã đăng ký");
        }

        if (roomCode != null && !roomCode.trim().isEmpty()) {
            if (!room.getRoomCode().equalsIgnoreCase(roomCode))
                throw new RuntimeException("Mã phòng thi sai");
        } else {
            return Map.of("valid", true, "needsCode", true);
        }

        if (room.getMaxAttempts() > 0 && studentId != null) {
            if (submissionRepository.countByRoomIdAndStudentId(roomId, studentId) >= room.getMaxAttempts())
                throw new RuntimeException("Bạn đã hết lượt làm bài");
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.util.Optional<RoomAttempt> existing = (studentId != null)
                ? roomAttemptRepository.findByRoomIdAndStudentId(roomId, studentId)
                : roomAttemptRepository.findByRoomIdAndStudentName(roomId, studentName);

        RoomAttempt attempt;
        if (existing.isPresent()) {
            attempt = existing.get();
            // Check if student should start a new attempt
            // If they are entering again, and the current attempt is expired OR they have
            // already submitted
            // We reset the timer.
            // Note: Continuous timer means we only reset if they are starting a FRESH
            // attempt.
            // If they just refreshed during an active attempt, we KEEP the old endTime.

            boolean isExpired = now.isAfter(attempt.getEndTime());
            boolean alreadySubmitted = false;
            if (studentId != null) {
                alreadySubmitted = submissionRepository.existsByRoomIdAndStudentIdAndSubmittedAtAfter(
                        roomId, studentId, attempt.getStartTime());
            } else {
                alreadySubmitted = submissionRepository.existsByRoomIdAndStudentNameAndSubmittedAtAfter(
                        roomId, studentName, attempt.getStartTime());
            }

            if (isExpired || alreadySubmitted) {
                attempt.setStartTime(now);
                attempt.setEndTime(now.plusMinutes(room.getDurationMinutes()));
                attempt = roomAttemptRepository.save(attempt);
            }
        } else {
            attempt = new RoomAttempt();
            attempt.setRoomId(roomId);
            attempt.setStudentId(studentId);
            attempt.setStudentName(studentName);
            attempt.setStartTime(now);
            attempt.setEndTime(now.plusMinutes(room.getDurationMinutes()));
            attempt = roomAttemptRepository.save(attempt);
        }

        return Map.of("valid", true, "roomName", room.getName(), "startTime", attempt.getStartTime(), "endTime",
                attempt.getEndTime());
    }

    public Map<String, Object> getRoomPublic(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        autoUpdateStatus(room);
        if ("pending".equals(room.getStatus()))
            throw new RuntimeException("Phòng thi chưa mở");
        if ("closed".equals(room.getStatus()))
            throw new RuntimeException("Phòng thi đã đóng");

        Exam exam = examRepository.findById(room.getExamId()).orElseThrow();
        return Map.of("room", toResponse(room), "questions",
                questionRepository.findByExamIdOrderByOrderIndex(room.getExamId()), "shuffled", exam.isShuffled());
    }

    public Submission submitRoom(String roomId, SubmissionRequest request) {
        ExamRoom room = examRoomRepository.findById(roomId).orElseThrow();
        autoUpdateStatus(room);
        if (!"open".equals(room.getStatus()))
            throw new RuntimeException("Phòng thi không mở");

        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(room.getExamId());
        float totalEarned = 0;
        int correctCount = 0;
        boolean hasEssay = false;

        for (Question q : questions) {
            List<String> ans = request.getAnswers().get(q.getId());
            if ("essay".equals(q.getType())) {
                hasEssay = true;
                String text = (ans != null && !ans.isEmpty()) ? ans.get(0) : "";
                if (!text.trim().isEmpty()) {
                    Map<String, Object> eval = geminiService.evaluateEssay(q.getContent(), q.getSampleAnswer(),
                            q.getScoringCriteria(), text);
                    totalEarned += ((Double) eval.get("score")).floatValue();
                }
            } else if (ans != null && q.getCorrectAnswers() != null) {
                if (new HashSet<>(ans).equals(new HashSet<>(q.getCorrectAnswers()))) {
                    totalEarned += 10;
                    correctCount++;
                }
            }
        }

        Submission s = new Submission();
        s.setRoomId(roomId);
        s.setExamId(room.getExamId());
        s.setStudentName(request.getStudentName());
        s.setStudentId(request.getStudentId());
        s.setTotalQuestions(questions.size());
        // Score and correctCount will be updated after processing individual answers
        s.setGradingStatus(hasEssay ? "ai_graded_essay" : "auto_graded");
        s.setStartedAt(LocalDateTime.now().minusMinutes(room.getDurationMinutes()));
        s.setSubmittedAt(LocalDateTime.now());
        s.setQuestionSnapshot(new ArrayList<>(questions));
        Submission saved = submissionRepository.save(s);

        for (Question q : questions) {
            List<String> ans = request.getAnswers().get(q.getId());
            if (ans != null) {
                SubmissionAnswer sa = new SubmissionAnswer();
                sa.setSubmissionId(saved.getId());
                sa.setQuestionId(q.getId());
                sa.setSelectedAnswer(ans);
                if ("essay".equals(q.getType())) {
                    String text = ans.get(0);
                    sa.setEssayAnswer(text);
                    if (!text.trim().isEmpty()) {
                        Map<String, Object> eval = geminiService.evaluateEssay(q.getContent(), q.getSampleAnswer(),
                                q.getScoringCriteria(), text);
                        float score = ((Double) eval.get("score")).floatValue();
                        sa.setAiScore(score);
                        sa.setFinalScore(score); // Default to AI score
                        sa.setAiComment((String) eval.get("feedback"));
                    }
                } else {
                    boolean correct = new HashSet<>(ans)
                            .equals(new HashSet<>(q.getCorrectAnswers() != null ? q.getCorrectAnswers() : List.of()));
                    sa.setCorrect(correct);
                    float pointShare = questions.isEmpty() ? 0 : 10.0f / questions.size();
                    sa.setFinalScore(correct ? pointShare : 0);
                }
                submissionAnswerRepository.save(sa);
            }
        }

        // Finalize submission score on scale 10
        List<SubmissionAnswer> allSaved = submissionAnswerRepository.findBySubmissionId(saved.getId());
        long finalCorrectCount = allSaved.stream().filter(SubmissionAnswer::isCorrect).count();
        float finalScore = questions.isEmpty() ? 0 : ((float) finalCorrectCount / questions.size()) * 10;
        saved.setScore(finalScore);
        saved.setCorrectCount((int) finalCorrectCount);
        submissionRepository.save(saved);

        return saved;
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
                room.isShowAnswerAfter(),
                room.isRequireStudentList(),
                room.getStatus(),
                submissionRepository.countByRoomId(room.getId()),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }
}
