package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.room.*;
import com.examify.examify.backend.model.*;
import com.examify.examify.backend.repository.*;
import com.examify.examify.backend.dto.exam.SubmissionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
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
        if ("closed".equals(room.getStatus())) return;
        
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

    public void deleteRoom(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Bạn không có quyền xóa phòng thi này");
        }

        if ("open".equals(room.getStatus())) {
            throw new RuntimeException("Không thể xóa phòng đang mở");
        }

        if (submissionRepository.existsByRoomId(roomId)) {
            throw new RuntimeException("Không thể xóa phòng đã có học sinh làm bài");
        }

        studentListRepository.deleteByRoomId(roomId);
        examRoomRepository.deleteById(roomId);
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

    public List<StudentListResponse> uploadStudentList(String roomId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File rỗng");
        }

        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        List<StudentList> students = new ArrayList<>();
        String fileName = file.getOriginalFilename();
        
        try {
            if (fileName != null && (fileName.endsWith(".xlsx") || fileName.endsWith(".xls"))) {
                students = parseExcel(file, roomId);
            } else if (fileName != null && fileName.endsWith(".docx")) {
                students = parseDocx(file, roomId);
            } else if (fileName != null && fileName.endsWith(".pdf")) {
                students = parsePdf(file, roomId);
            } else {
                students = parseTextFile(file, roomId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc file: " + e.getMessage());
        }

        if (students.isEmpty()) {
            throw new RuntimeException("Không tìm thấy dữ liệu học sinh. Định dạng: MSSV, Họ tên");
        }

        studentListRepository.deleteByRoomId(roomId);
        studentListRepository.saveAll(students);
        
        return getStudentList(roomId);
    }

    public StudentListResponse updateStudentManual(String roomId, String id, StudentListRequest.StudentEntry entry) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        StudentList student = studentListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy học sinh"));
        
        // Check if new SID exists in other entries (optional, but good)
        if (!student.getStudentId().equals(entry.getStudentId()) && 
            studentListRepository.existsByRoomIdAndStudentId(roomId, entry.getStudentId())) {
            throw new RuntimeException("Học sinh với MSSV " + entry.getStudentId() + " đã tồn tại trong danh sách");
        }

        student.setStudentId(entry.getStudentId());
        student.setStudentName(entry.getStudentName());
        
        StudentList saved = studentListRepository.save(student);
        
        StudentListResponse resp = new StudentListResponse();
        resp.setId(saved.getId());
        resp.setRoomId(saved.getRoomId());
        resp.setStudentId(saved.getStudentId());
        resp.setStudentName(saved.getStudentName());
        resp.setHasSubmitted(!submissionRepository.findByRoomIdAndStudentId(roomId, saved.getStudentId()).isEmpty());
        return resp;
    }

    public StudentListResponse addStudentManual(String roomId, StudentListRequest.StudentEntry entry) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        if (studentListRepository.existsByRoomIdAndStudentId(roomId, entry.getStudentId())) {
            throw new RuntimeException("Học sinh với MSSV " + entry.getStudentId() + " đã tồn tại trong danh sách");
        }

        StudentList student = new StudentList();
        student.setRoomId(roomId);
        student.setStudentId(entry.getStudentId());
        student.setStudentName(entry.getStudentName());
        
        StudentList saved = studentListRepository.save(student);
        
        StudentListResponse resp = new StudentListResponse();
        resp.setId(saved.getId());
        resp.setRoomId(saved.getRoomId());
        resp.setStudentId(saved.getStudentId());
        resp.setStudentName(saved.getStudentName());
        resp.setHasSubmitted(false);
        return resp;
    }

    public void deleteStudent(String roomId, String id) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        studentListRepository.deleteById(id);
    }

    private List<StudentList> parseTextFile(MultipartFile file, String roomId) throws Exception {
        List<StudentList> students = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("[,;\t]");
                if (parts.length >= 2) {
                    StudentList student = new StudentList();
                    student.setRoomId(roomId);
                    student.setStudentId(parts[0].trim());
                    student.setStudentName(parts[1].trim());
                    students.add(student);
                }
            }
        }
        return students;
    }

    private List<StudentList> parseDocx(MultipartFile file, String roomId) throws Exception {
        List<StudentList> students = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    if (cells.size() >= 2) {
                        String sid = cells.get(0).getText().trim();
                        String name = cells.get(1).getText().trim();
                        
                        // Nếu cột đầu tiên là STT (số thứ tự), hãy thử lấy cột 1 và 2
                        if (sid.matches("\\d+") && cells.size() >= 3) {
                            sid = cells.get(1).getText().trim();
                            name = cells.get(2).getText().trim();
                        }

                        if (!sid.isEmpty() && !"MSSV".equalsIgnoreCase(sid) && !"Mã số".equalsIgnoreCase(sid) && !"STT".equalsIgnoreCase(sid)) {
                            StudentList student = new StudentList();
                            student.setRoomId(roomId);
                            student.setStudentId(sid);
                            student.setStudentName(name);
                            students.add(student);
                        }
                    }
                }
            }
        }
        return students;
    }

    private List<StudentList> parsePdf(MultipartFile file, String roomId) throws Exception {
        List<StudentList> students = new ArrayList<>();
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    // Cố gắng tìm MSSV (thường là số)
                    String sid = "";
                    String name = "";
                    
                    if (parts[0].matches("\\d{5,10}")) { // MSSV thường dài
                        sid = parts[0];
                        name = line.replace(sid, "").trim();
                    } else if (parts.length >= 3 && parts[1].matches("\\d{5,10}")) { // Có STT ở đầu
                        sid = parts[1];
                        name = line.substring(line.indexOf(sid) + sid.length()).trim();
                    }

                    if (!sid.isEmpty()) {
                        StudentList student = new StudentList();
                        student.setRoomId(roomId);
                        student.setStudentId(sid);
                        student.setStudentName(name);
                        students.add(student);
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
                // Attempt to identify headers
                if (idCol == -1 && nameCol == -1) {
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        String cellVal = getCellValueAsString(row.getCell(i));
                        if (cellVal == null) continue;
                        cellVal = cellVal.toLowerCase();
                        if (cellVal.contains("mã số") || cellVal.contains("mssv") || cellVal.contains("student id")) {
                            idCol = i;
                        } else if (cellVal.contains("họ và tên") || cellVal.contains("họ tên") || cellVal.contains("name")) {
                            nameCol = i;
                        }
                    }
                    if (idCol != -1 && nameCol != -1) continue; // Skip header row
                }
                
                String col0 = getCellValueAsString(row.getCell(0));
                String col1 = getCellValueAsString(row.getCell(1));
                String col2 = getCellValueAsString(row.getCell(2));
                
                String sid = "";
                String name = "";
                
                if (idCol != -1 && nameCol != -1) {
                    sid = getCellValueAsString(row.getCell(idCol));
                    name = getCellValueAsString(row.getCell(nameCol));
                } else if (col0 != null && col1 != null) {
                    if (col0.matches("\\d+") && col2 != null && !col2.isEmpty()) { 
                        sid = col1;
                        name = col2;
                    } else {
                        sid = col0;
                        name = col1;
                    }
                }
                
                if (sid != null && !sid.isEmpty() && 
                    !sid.toLowerCase().contains("mssv") && 
                    !sid.toLowerCase().contains("mã số") &&
                    !sid.toLowerCase().contains("stt")) {
                    StudentList student = new StudentList();
                    student.setRoomId(roomId);
                    student.setStudentId(sid.trim());
                    student.setStudentName(name != null ? name.trim() : "");
                    students.add(student);
                }
            }
        }
        return students;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }

    public List<StudentListResponse> getStudentList(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        List<StudentList> list = studentListRepository.findByRoomId(roomId);
        return list.stream().map(s -> {
            StudentListResponse resp = new StudentListResponse();
            resp.setId(s.getId());
            resp.setRoomId(s.getRoomId());
            resp.setStudentId(s.getStudentId());
            resp.setStudentName(s.getStudentName());
            resp.setHasSubmitted(!submissionRepository.findByRoomIdAndStudentId(roomId, s.getStudentId()).isEmpty());
            return resp;
        }).toList();
    }

    public List<SubmissionSummaryResponse> getRoomSubmissions(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        List<Submission> submissions = submissionRepository.findByRoomId(roomId);
        // Sắp xếp theo submittedAt desc
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
            return resp;
        }).toList();
    }

    public SubmissionDetailResponse getSubmissionDetail(String roomId, String submissionId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài nộp"));

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

        // Lấy snapshot questions
        List<Object> snapshot = s.getQuestionSnapshot();
        
        List<SubmissionDetailResponse.AnswerDetailResponse> answerDetails = answers.stream().map(ans -> {
            SubmissionDetailResponse.AnswerDetailResponse detail = new SubmissionDetailResponse.AnswerDetailResponse();
            detail.setQuestionId(ans.getQuestionId());
            
            // Tìm question trong snapshot
            Optional<Question> qOpt = snapshot.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .filter(map -> ans.getQuestionId().equals(map.get("id")))
                .map(map -> {
                    Question q = new Question();
                    q.setId((String) map.get("id"));
                    q.setContent((String) map.get("content"));
                    q.setType((String) map.get("type"));
                    q.setExplanation((String) map.get("explanation"));
                    q.setSampleAnswer((String) map.get("sampleAnswer"));
                    
                    if (map.get("choices") != null) {
                        List<Map<String, String>> choicesMap = (List<Map<String, String>>) map.get("choices");
                        q.setChoices(choicesMap.stream().map(c -> {
                            Question.Choice choice = new Question.Choice();
                            choice.setKey(c.get("key"));
                            choice.setContent(c.get("content"));
                            return choice;
                        }).toList());
                    }
                    
                    if (map.get("correctAnswers") != null) {
                        q.setCorrectAnswers((List<String>) map.get("correctAnswers"));
                    }
                    
                    return q;
                }).findFirst();

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
        }).toList();

        resp.setAnswers(answerDetails);
        return resp;
    }

    public SubmissionDetailResponse gradeEssay(String roomId, String submissionId, GradeEssayRequest req) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        if (!room.getTeacherId().equals(getCurrentTeacherId())) {
            throw new RuntimeException("Quyền hạn không hợp lệ");
        }

        SubmissionAnswer answer = submissionAnswerRepository.findById(req.getSubmissionAnswerId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu trả lời"));

        answer.setFinalScore(req.getFinalScore());
        answer.setManuallyGraded(true);
        submissionAnswerRepository.save(answer);

        if (req.isConfirm()) {
            Submission s = submissionRepository.findById(submissionId).orElseThrow();
            List<SubmissionAnswer> allAnswers = submissionAnswerRepository.findBySubmissionId(submissionId);
            
            boolean allEssayGraded = allAnswers.stream()
                .filter(ans -> {
                    // Check if it's essay by looking at snapshot? 
                    // To be simple, if it has essayAnswer and is not auto-correct
                    return ans.getEssayAnswer() != null; 
                })
                .allMatch(SubmissionAnswer::isManuallyGraded);

            if (allEssayGraded) {
                float totalScore = (float) allAnswers.stream()
                    .mapToDouble(SubmissionAnswer::getFinalScore)
                    .sum();
                s.setScore(totalScore);
                s.setGradingStatus("fully_graded");
                submissionRepository.save(s);
            }
        }

        return getSubmissionDetail(roomId, submissionId);
    }

    public Map<String, Object> validateStudent(String roomId, String studentId, String studentName, String roomCode) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));

        autoUpdateStatus(room);
        if (!"open".equals(room.getStatus())) {
            throw new RuntimeException("Phòng thi hiện không ở trạng thái mở");
        }

        // 1. Verify room code
        if (!room.getRoomCode().equalsIgnoreCase(roomCode)) {
            throw new RuntimeException("Mã phòng thi không chính xác");
        }

        // 2. Validate student list if required
        if (room.isRequireStudentList()) {
            if (studentId == null || studentId.trim().isEmpty()) {
                throw new RuntimeException("Phòng thi yêu cầu nhập Mã số học sinh");
            }
            if (!studentListRepository.existsByRoomIdAndStudentId(roomId, studentId)) {
                throw new RuntimeException("Mã số học sinh " + studentId + " không có trong danh sách dự thi");
            }
        }

        // 3. Check attempts
        if (room.getMaxAttempts() > 0 && studentId != null && !studentId.trim().isEmpty()) {
            long attempts = submissionRepository.countByRoomIdAndStudentId(roomId, studentId);
            if (attempts >= room.getMaxAttempts()) {
                throw new RuntimeException("Bạn đã hết lượt làm bài (Tối đa " + room.getMaxAttempts() + " lần)");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("roomName", room.getName());
        return result;
    }

    // ===== PUBLIC METHODS FOR STUDENTS =====

    public Map<String, Object> getRoomPublic(String roomId) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        autoUpdateStatus(room);
        
        if ("pending".equals(room.getStatus())) {
            throw new RuntimeException("Phòng thi chưa đến giờ mở");
        }
        if ("closed".equals(room.getStatus())) {
            throw new RuntimeException("Phòng thi đã đóng");
        }

        Exam exam = examRepository.findById(room.getExamId()).orElseThrow();
        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(room.getExamId());
        
        Map<String, Object> resp = new HashMap<>();
        resp.put("room", toResponse(room));
        resp.put("questions", questions);
        resp.put("shuffled", exam.isShuffled());
        return resp;
    }

    public Submission submitRoom(String roomId, SubmissionRequest request) {
        ExamRoom room = examRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng thi"));
        
        autoUpdateStatus(room);
        if (!"open".equals(room.getStatus())) {
            throw new RuntimeException("Phòng thi không ở trạng thái mở");
        }

        // Validate student if required
        if (room.isRequireStudentList()) {
            if (request.getStudentId() == null || request.getStudentId().trim().isEmpty()) {
                throw new RuntimeException("Phòng thi yêu cầu nhập Mã số học sinh (Student ID)");
            }
            if (!studentListRepository.existsByRoomIdAndStudentId(roomId, request.getStudentId())) {
                throw new RuntimeException("Mã số học sinh không nằm trong danh sách được phép dự thi");
            }
            
            // Check max attempts
            if (room.getMaxAttempts() > 0) {
                long attempts = submissionRepository.countByRoomIdAndStudentId(roomId, request.getStudentId());
                if (attempts >= room.getMaxAttempts()) {
                    throw new RuntimeException("Bạn đã hết lượt làm bài (Tối đa " + room.getMaxAttempts() + " lần)");
                }
            }
        }

        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(room.getExamId());
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
        submission.setRoomId(roomId);
        submission.setExamId(room.getExamId());
        submission.setStudentName(request.getStudentName());
        submission.setStudentId(request.getStudentId());
        submission.setScore(finalScore);
        submission.setTotalQuestions(totalQuestions);
        submission.setCorrectCount(correctCount);
        submission.setGradingStatus(hasEssay ? "ai_graded_essay" : "auto_graded");
        submission.setStartedAt(LocalDateTime.now().minusMinutes(room.getDurationMinutes()));
        submission.setSubmittedAt(LocalDateTime.now());
        
        // Save snapshot
        submission.setQuestionSnapshot(new ArrayList<>(questions));

        Submission saved = submissionRepository.save(submission);

        // Save answers
        for (Question q : questions) {
            List<String> ans = request.getAnswers().get(q.getId());
            if (ans != null) {
                SubmissionAnswer sa = new SubmissionAnswer();
                sa.setSubmissionId(saved.getId());
                sa.setQuestionId(q.getId());
                sa.setSelectedAnswer(ans);
                if ("essay".equals(q.getType())) {
                    String text = (ans != null && !ans.isEmpty()) ? ans.get(0) : "";
                    sa.setEssayAnswer(text);
                    if (!text.trim().isEmpty()) {
                        Map<String, Object> aiResult = geminiService.evaluateEssay(
                            q.getContent(), q.getSampleAnswer(), q.getScoringCriteria(), text
                        );
                        sa.setAiScore(((Double) aiResult.get("score")).floatValue());
                        sa.setAiComment((String) aiResult.get("feedback"));
                    }
                } else {
                    Set<String> sSet = new HashSet<>(ans);
                    Set<String> cSet = new HashSet<>(q.getCorrectAnswers() != null ? q.getCorrectAnswers() : List.of());
                    sa.setCorrect(sSet.equals(cSet) && !sSet.isEmpty());
                }
                submissionAnswerRepository.save(sa);
            }
        }

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
                room.getUpdatedAt()
        );
    }
}
