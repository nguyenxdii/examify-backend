package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.ai.*;
import com.examify.examify.backend.dto.exam.QuestionRequest;
import com.examify.examify.backend.model.Question;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class GeminiService {

    @Value("${gemini.api-keys}")
    private String rawApiKeys;

    private int currentKeyIndex = 0;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=";

    // ===== BƯỚC 1: Validate nội dung (Chủ đề tự do) =====
    public ValidateResponse validate(AnalyzeRequest request) {
        String prompt = buildValidatePrompt(request.getContent(), request.getInputType());
        String raw = callGemini(prompt);
        return parseValidateResponse(raw);
    }

    // ===== BƯỚC 2: Phân tích nội dung (Sau khi trích xuất) =====
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String prompt = buildAnalyzePrompt(request);
        String raw = callGemini(prompt);
        return parseAnalyzeResponse(raw);
    }

    // ===== BƯỚC 4: Sinh câu hỏi (Gồm Validation + Generation) =====
    public GenerateAiResponse generate(GenerateRequest request) {
        try {
            String prompt = buildGeneratePrompt(request);
            String raw = callGemini(prompt);
            GenerateAiResponse response = parseGenerateResponse(raw);
            System.out.println("--- [FINAL RESPONSE OBJECT] ---");
            System.out.println("isValid: " + response.isValid());
            System.out.println("questions count: " + (response.getQuestions() != null ? response.getQuestions().size() : "null"));
            return response;
        } catch (Exception e) {
            String detailedError = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định";
            System.err.println("Error in generate: " + detailedError);
            return GenerateAiResponse.builder()
                .isValid(false)
                .reason("AI Failure: " + detailedError)
                .build();
        }
    }

    public AnalyzeResponse analyzeFile(MultipartFile file) {
        try {
            String content = extractTextFromFile(file);
            AnalyzeRequest request = new AnalyzeRequest();
            request.setContent(content);
            request.setInputType("document");
            request.setLanguage("vi"); // Default
            return analyze(request);
        } catch (Exception e) {
            System.err.println("Error in analyzeFile: " + e.getMessage());
            AnalyzeResponse response = new AnalyzeResponse(0, 0, 0, 0, List.of(), "Lỗi khi xử lý file: " + e.getMessage());
            response.setSufficient(false);
            return response;
        }
    }

    public String extractTextFromFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) return "";

        try {
            if (filename.toLowerCase().endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                }
            } else if (filename.toLowerCase().endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(file.getInputStream());
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            } else if (filename.toLowerCase().endsWith(".txt")) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi trích xuất nội dung file: " + e.getMessage());
        }
        return "";
    }

    private List<String> getApiKeys() {
        return Arrays.asList(rawApiKeys.split(","));
    }

    // ===== GỌI GEMINI API =====
    private String callGemini(String prompt) {
        List<String> keys = getApiKeys();
        int attempts = 0;

        while (attempts < keys.size()) {
            String apiKey = keys.get(currentKeyIndex).trim();
            String url = GEMINI_URL + apiKey;

            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                    "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                    "temperature", 0.7,
                    "maxOutputTokens", 8192
                )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // Kiểm tra nếu bị chặn bởi Safety hoặc lỗi khác
                JsonNode candidates = root.path("candidates");
                if (candidates.isMissingNode() || !candidates.has(0)) {
                    // Thử tìm error message từ API
                    String errorMessage = root.path("error").path("message").asText(null);
                    if (errorMessage != null) throw new RuntimeException("Gemini API Error: " + errorMessage);
                    throw new RuntimeException("Gemini API từ chối phản hồi (có thể do bộ lọc an toàn).");
                }
                
                JsonNode firstCandidate = candidates.get(0);
                String finishReason = firstCandidate.path("finishReason").asText("");
                if ("SAFETY".equals(finishReason)) {
                    throw new RuntimeException("Nội dung bị chặn bởi bộ lọc an toàn của Google Gemini.");
                }

                String result = firstCandidate.path("content").path("parts").get(0)
                           .path("text").asText();
                
                // Logging feedback for debugging
                System.out.println("--- [GEMINI RESPONSE] ---");
                System.out.println(result);
                System.out.println("-------------------------");
                
                return result;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                // Nếu lỗi do hết quota (429), key không hợp lệ (401) hoặc máy chủ quá tải (503)
                if (msg.contains("429") || msg.contains("401") || msg.contains("503")) {
                    System.out.println("Gemini API Busy/Error (503/429/401). Retrying with next key in 1s...");
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    currentKeyIndex = (currentKeyIndex + 1) % keys.size();
                    attempts++;
                    continue;
                }
                throw new RuntimeException("Lỗi khi gọi Gemini API: " + msg);
            }
        }
        throw new RuntimeException("Tất cả API Key đều hết hạn hoặc không khả dụng.");
    }

    // ===== PROMPT VALIDATE & EXTRACT =====
    private String buildValidatePrompt(String content, String inputType) {
        String typeDesc = "topic".equals(inputType) ? "Chủ đề tự do" : "Văn bản tài liệu";
        return """
            Bạn là bộ lọc thông minh cho hệ thống tạo đề thi giáo dục.
            Loại input: %s
            
            Nội dung user nhập:
            ---
            %s
            ---
            
            BƯỚC 1 — PHÂN TÍCH NỘI DUNG:
            Đọc kỹ toàn bộ nội dung. Kiểm tra xem có chứa bất kỳ thông tin nào
            liên quan đến chủ đề học thuật, môn học, bài học, hoặc kiến thức
            có thể dùng để tạo câu hỏi thi không.
            
            Lưu ý quan trọng: User có thể gửi câu hỗn hợp kiểu
            "Chào bạn, giúp mình tạo đề về [chủ đề X] nhé" — trong trường hợp
            này PHẢI extract chủ đề X và coi là HỢP LỆ.
            
            KHÔNG HỢP LỆ khi và chỉ khi:
            - Toàn bộ nội dung chỉ là chào hỏi/spam/vô nghĩa, KHÔNG có bất kỳ
              chủ đề học thuật nào có thể extract được
            - Chỉ ghi tên môn học không có context gì thêm ("Toán", "Lịch sử")
            - Không đủ thông tin để tạo ít nhất 5 câu hỏi mà không cần bịa
            
            HỢP LỆ khi:
            - Có thể extract được ít nhất 1 chủ đề học thuật rõ ràng
            - Dù có lời chào hay câu yêu cầu bên ngoài, phần nội dung học thuật
              vẫn đủ để tạo câu hỏi
            
            BƯỚC 2 — TRẢ VỀ JSON:
            Nếu HỢP LỆ:
            {
              "isValid": true,
              "extractedTopic": "chủ đề đã extract được, bỏ phần chào hỏi",
              "reason": "Đã tìm thấy chủ đề: [tên chủ đề]",
              "detectedLanguage": "vi"
            }
            
            Nếu KHÔNG HỢP LỆ:
            {
              "isValid": false,
              "extractedTopic": null,
              "reason": "lý do thân thiện, gợi ý cụ thể user nên nhập gì",
              "detectedLanguage": "vi"
            }
            
            QUY TẮC BẮT BUỘC:
            - Trả về JSON thuần, tuyệt đối không có markdown, không có ```
            - reason phải viết bằng ngôn ngữ của user (vi hoặc en)
            - reason khi isValid=false phải thân thiện, không phán xét,
              ví dụ: "Nội dung chưa rõ chủ đề học tập. Bạn có thể nhập kiểu:
              'Lịch sử Việt Nam thế kỷ 20' hoặc 'Hàm số lớp 10 chương 2' nhé!"
            - Nếu isValid=true, extractedTopic sẽ được dùng thay content gốc
              để generate câu hỏi → phải đủ rõ ràng
            - maxOutputTokens cho call này: 200, không cần nhiều hơn
            """.formatted(typeDesc, content);
    }

    // ===== PROMPT GENERATE (Validate + Generate) =====
    private String buildGeneratePrompt(GenerateRequest request) {
        String lang = "en".equals(request.getLanguage()) ? "English" : "Vietnamese";
        String difficultyNote = buildDifficultyNote(request);

        return """
            Bạn là một trợ lý giáo dục thông minh.
            
            NHIỆM VỤ:
            Dựa trên nội dung/chủ đề dưới đây, hãy tạo bộ câu hỏi thi chất lượng.
            
            Nội dung user cung cấp:
            ---
            %s
            ---
            
            YÊU CẦU CẤU TRÚC ĐỀ THI:
            - Trắc nghiệm 1 đáp án (multiple_choice): %d câu
            - Trắc nghiệm nhiều đáp án (multiple_answer): %d câu
            - Tự luận (essay): %d câu
            - %s
            
            QUY TẮC XỬ LÝ:
            1. Nếu nội dung là một chủ đề (như "Toán 12", "Hồ Chí Minh"): Hãy dùng kiến thức chuyên gia để soạn đề.
            2. Nếu nội dung là câu yêu cầu (ví dụ: "tạo cho mình đề về toán lớp 4 nhé"): Hãy tự động trích xuất chủ đề chính (toán lớp 4) và tiến hành soạn đề.
            3. Nếu nội dung là yêu cầu SINH LẠI một câu hỏi (Ví dụ: "Hãy sinh lại câu hỏi sau..."):
               - Hãy tạo ra một câu hỏi MỚI hoàn toàn, khai thác khía cạnh khác của cùng CHỦ ĐỀ.
               - Tuyệt đối không được giữ nguyên cấu trúc cũ và chỉ thay đổi số liệu/tên gọi.
               - Đảm bảo giữ đúng ĐỘ KHÓ và LOẠI CÂU HỎI (multiple_choice/multiple_answer/essay).
               - Đảm bảo câu hỏi mới không trùng lặp nội dung với câu cũ.
            4. Nếu nội dung là văn bản dài: Hãy bám sát kiến thức trong văn bản.
            4. Nếu nội dung hoàn toàn vô nghĩa (ví dụ "abc", "123", "...") hoặc vi phạm an toàn: 
               - Đặt "isValid": false
               - "reason": "Lý do cụ thể giúp người dùng sửa lại"
               - "questions": []
            5. Ngược lại:
               - Đặt "isValid": true
               - "reason": "Nội dung hợp lệ"
               - "suggestedTitle": "Tên đề thi phù hợp"
               - "questions": [danh sách câu hỏi]
            
            TRẢ VỀ JSON TUYỆT ĐỐI (không markdown):
            {
              "isValid": true/false,
              "reason": "...",
              "suggestedTitle": "...",
              "questions": [
                {
                  "content": "...",
                  "type": "multiple_choice/multiple_answer/essay",
                  "choices": [{"key":"A","content":"..."}, ...],
                  "correctAnswers": ["A"],
                  "sampleAnswer": "...",
                  "scoringCriteria": "...",
                  "explanation": "...",
                  "difficulty": "easy/medium/hard",
                  "topic": "...",
                  "tags": ["..."]
                }
              ]
            }
            
            QUY TẮC BẮT BUỘC:
            - Trả về JSON thuần, tuyệt đối không có markdown, không có ```
            - multiple_choice: correctAnswers có đúng 1 phần tử
            - multiple_answer: correctAnswers có 2+ phần tử
            - essay: choices=[], correctAnswers=[], phải có sampleAnswer và scoringCriteria
            - Câu hỏi rõ ràng, chính xác, không trùng lặp
            - Toàn bộ nội dung câu hỏi viết bằng %s
            """.formatted(
                request.getContent(),
                request.getMultipleChoice(),
                request.getMultipleAnswer(),
                request.getEssay(),
                difficultyNote,
                lang
            );
    }

    // ===== PROMPT PHÂN TÍCH CHI TIẾT =====
    private String buildAnalyzePrompt(AnalyzeRequest request) {
        String typeDesc = "topic".equals(request.getInputType()) ? "chủ đề" : "tài liệu văn bản";
        String langName = "en".equals(request.getLanguage()) ? "English" : "Tiếng Việt";

        return """
            Bạn là chuyên gia giáo dục. Nhiệm vụ của bạn là phân tích %s sau đây bằng ngôn ngữ %s.
            
            Nội dung:
            %s
            
            ===== BƯỚC 1: KIỂM TRA TÍNH PHÙ HỢP (NGHIÊM NGẶT) =====
            Bạn PHẢI trả lời isSufficient = false nếu:
            - Nội dung là DANH SÁCH cá nhân: Tên học sinh, mã số sinh viên, giới tính, bảng điểm cá nhân, danh sách nhân viên. (TUYỆT ĐỐI không dùng những thứ này để tạo câu hỏi thống kê/xử lý thông tin).
            - Nội dung chỉ là chào hỏi: "Chào bạn", "Tôi là Duy", "Giúp tôi với".
            - Nội dung vô nghĩa/spam: "abc", "123", "vcl".
            - Nội dung mang tính chất riêng tư, không phải kiến thức học thuật.
            
            Bạn CHỈ trả lời isSufficient = true nếu:
            - Đây là một bài học, tài liệu lý thuyết, kiến thức khoa học, lịch sử, văn học hoặc bài tập cụ thể.
            
            ===== BƯỚC 2: GỢI Ý (TỔNG CỘNG TỐI ĐA 50 CÂU) =====
            Nếu isSufficient = true, hãy phân chia số câu hỏi hợp lý (tổng không quá 50) sao cho phù hợp với nội dung:
            1. Phân chia theo LOẠI CÂU:
               - suggestedMultipleChoice (TN 1 đáp án)
               - suggestedMultipleAnswer (TN nhiều đáp án)
               - suggestedEssay (Tự luận)
               => Tổng 3 cái này phải <= 50 (Nên gợi ý khoảng 10-50 tùy độ dài tài liệu).
            2. Phân chia theo ĐỘ KHÓ:
               - suggestedEasy (Dễ)
               - suggestedMedium (Vừa)
               - suggestedHard (Khó)
               => Tổng 3 cái này phải BẰNG đúng tổng ở phần loại câu.
            
            ===== QUY TẮC NGÔN NGỮ =====
            Các field: message, suggestedTitle, suggestedDescription, detectedTopics, summary 
            phải bằng %s.
            
            ===== ĐỊNH DẠNG JSON (KHÔNG MARKDOWN) =====
            {
              "isSufficient": true,
              "message": "Nội dung hợp lệ.",
              "suggestedTitle": "...",
              "suggestedDescription": "...",
              "suggestedMultipleChoice": 30,
              "suggestedMultipleAnswer": 15,
              "suggestedEssay": 5,
              "suggestedEasy": 20,
              "suggestedMedium": 20,
              "suggestedHard": 10,
              "detectedTopics": [...],
              "summary": "..."
            }
            """.formatted(typeDesc, langName, request.getContent(), langName);
    }

    // ===== PROMPT SINH CÂU HỎI (BẢN CŨ - KHÔNG DÙNG) =====
    private String buildGenerateOldPrompt(GenerateRequest request) {
        String difficultyNote = buildDifficultyNote(request);

        return """
            Bạn là chuyên gia giáo dục. Sinh câu hỏi từ nội dung sau.
            
            Nội dung/Chủ đề: %s
            
            Yêu cầu:
            - Trắc nghiệm 1 đáp án (multiple_choice): %d câu
            - Trắc nghiệm nhiều đáp án (multiple_answer): %d câu
            - Tự luận (essay): %d câu
            - %s
            
            Trả về JSON array (không có markdown, không có ```):
            [
              {
                "content": "Nội dung câu hỏi?",
                "type": "multiple_choice",
                "choices": [
                  {"key": "A", "content": "Đáp án A"},
                  {"key": "B", "content": "Đáp án B"},
                  {"key": "C", "content": "Đáp án C"},
                  {"key": "D", "content": "Đáp án D"}
                ],
                "correctAnswers": ["A"],
                "sampleAnswer": "Câu trả lời mẫu (nếu là tự luận)",
                "scoringCriteria": "Tiêu chí chấm điểm (nếu là tự luận)",
                "explanation": "Giải thích đáp án",
                "difficulty": "medium",
                "topic": "chủ đề con",
                "tags": ["tag1", "tag2"]
              }
            ]
            
            Lưu ý:
            - multiple_choice: correctAnswers chỉ có 1 phần tử
            - multiple_answer: correctAnswers có 2+ phần tử
            - essay: choices = [], correctAnswers = [], bắt buộc có sampleAnswer và scoringCriteria
            - Câu hỏi phải rõ ràng, chính xác, không trùng lặp
            - %s
            """.formatted(
                request.getContent(),
                request.getMultipleChoice(),
                request.getMultipleAnswer(),
                request.getEssay(),
                difficultyNote,
                request.isDetailedExplanation() ? "Yêu cầu giải thích (explanation) cực kỳ chi tiết, trình bày từng bước giải quyết vấn đề (step-by-step)." : "Giải thích ngắn gọn, súc tích."
            );
    }

    private String buildDifficultyNote(GenerateRequest request) {
        return "Số lượng câu theo mức độ: Dễ %d câu, Trung bình %d câu, Khó %d câu. Tổng cộng phải khớp với tổng số câu hỏi yêu cầu."
            .formatted(request.getEasyCount(),
                       request.getMediumCount(),
                       request.getHardCount());
    }

    // ===== PARSE RESPONSE =====
    private AnalyzeResponse parseAnalyzeResponse(String raw) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode node = objectMapper.readTree(cleaned);
            List<String> topics = new ArrayList<>();
            node.path("detectedTopics").forEach(t -> topics.add(t.asText()));
            
            AnalyzeResponse response = new AnalyzeResponse();
            response.setSuggestedTotal(50);
            response.setSuggestedMultipleChoice(node.path("suggestedMultipleChoice").asInt(30));
            response.setSuggestedMultipleAnswer(node.path("suggestedMultipleAnswer").asInt(15));
            response.setSuggestedEssay(node.path("suggestedEssay").asInt(5));
            response.setSuggestedEasy(node.path("suggestedEasy").asInt(20));
            response.setSuggestedMedium(node.path("suggestedMedium").asInt(20));
            response.setSuggestedHard(node.path("suggestedHard").asInt(10));
            response.setDetectedTopics(topics);
            response.setSummary(node.path("summary").asText(""));
            response.setSufficient(node.path("isSufficient").asBoolean(false));
            response.setMessage(node.path("message").asText(""));
            response.setSuggestedTitle(node.path("suggestedTitle").asText(""));
            response.setSuggestedDescription(node.path("suggestedDescription").asText(""));
            
            return response;
        } catch (Exception e) {
            AnalyzeResponse fallback = new AnalyzeResponse();
            fallback.setSufficient(false);
            fallback.setMessage("Lỗi phân tích phản hồi từ AI.");
            return fallback;
        }
    }

    private ValidateResponse parseValidateResponse(String raw) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode node = objectMapper.readTree(cleaned);
            return new ValidateResponse(
                node.path("isValid").asBoolean(false),
                node.path("extractedTopic").asText(null),
                node.path("reason").asText(""),
                node.path("detectedLanguage").asText("vi")
            );
        } catch (Exception e) {
            return new ValidateResponse(false, null, "Lỗi kết nối AI validation.", "vi");
        }
    }

    private GenerateAiResponse parseGenerateResponse(String raw) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode node = objectMapper.readTree(cleaned);
            boolean isValid = node.path("isValid").asBoolean(false);
            
            if (!isValid) {
                return GenerateAiResponse.builder()
                    .isValid(false)
                    .reason(node.path("reason").asText("Nội dung không phù hợp."))
                    .build();
            }

            List<QuestionRequest> questions = new ArrayList<>();
            JsonNode qsNode = node.path("questions");
            if (qsNode.isArray()) {
                for (JsonNode q : qsNode) {
                    questions.add(parseSingleQuestion(q));
                }
            }

            return GenerateAiResponse.builder()
                .isValid(true)
                .suggestedTitle(node.path("suggestedTitle").asText("Đề thi mới"))
                .questions(questions)
                .build();
        } catch (Exception e) {
            return GenerateAiResponse.builder()
                .isValid(false)
                .reason("Parse Error: Lỗi phân tích JSON từ AI (" + e.getMessage() + "). Phản hồi gốc: " + (raw != null && raw.length() > 100 ? raw.substring(0, 100) + "..." : raw))
                .build();
        }
    }

    private QuestionRequest parseSingleQuestion(JsonNode q) {
        QuestionRequest qr = new QuestionRequest();
        qr.setContent(q.path("content").asText());
        qr.setType(q.path("type").asText("multiple_choice"));
        qr.setDifficulty(q.path("difficulty").asText("medium"));
        qr.setExplanation(q.path("explanation").asText(""));
        qr.setTopic(q.path("topic").asText(""));
        qr.setSampleAnswer(q.path("sampleAnswer").asText(null));
        qr.setScoringCriteria(q.path("scoringCriteria").asText(null));
        
        // Tags
        List<String> tags = new ArrayList<>();
        if (q.has("tags")) {
            q.get("tags").forEach(t -> tags.add(t.asText()));
        }
        qr.setTags(tags);

        // Choices
        List<Question.Choice> choices = new ArrayList<>();
        if (q.has("choices")) {
            q.get("choices").forEach(c -> {
                Question.Choice choice = new Question.Choice();
                choice.setKey(c.path("key").asText());
                choice.setContent(c.path("content").asText());
                choices.add(choice);
            });
        }
        qr.setChoices(choices);

        // Correct Answers
        List<String> ans = new ArrayList<>();
        if (q.has("correctAnswers")) {
            q.get("correctAnswers").forEach(a -> ans.add(a.asText()));
        }
        qr.setCorrectAnswers(ans);

        return qr;
    }

    // ===== PARSE CÂU HỎI (BẢN CŨ - KHÔNG DÙNG) =====
    private List<QuestionRequest> parseGenerateOldResponse(String raw) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode array = objectMapper.readTree(cleaned);
            List<QuestionRequest> result = new ArrayList<>();

            for (JsonNode node : array) {
                QuestionRequest q = new QuestionRequest();
                q.setContent(node.path("content").asText());
                q.setType(node.path("type").asText("multiple_choice"));
                q.setSampleAnswer(node.path("sampleAnswer").asText());
                q.setScoringCriteria(node.path("scoringCriteria").asText());
                q.setExplanation(node.path("explanation").asText());
                q.setDifficulty(node.path("difficulty").asText("medium"));
                q.setTopic(node.path("topic").asText());

                // Parse choices
                List<Question.Choice> choices = new ArrayList<>();
                node.path("choices").forEach(c -> {
                    Question.Choice choice = new Question.Choice();
                    choice.setKey(c.path("key").asText());
                    choice.setContent(c.path("content").asText());
                    choices.add(choice);
                });
                q.setChoices(choices);

                // Parse correctAnswers
                List<String> answers = new ArrayList<>();
                node.path("correctAnswers").forEach(a -> answers.add(a.asText()));
                q.setCorrectAnswers(answers);

                // Parse tags
                List<String> tags = new ArrayList<>();
                node.path("tags").forEach(t -> tags.add(t.asText()));
                q.setTags(tags);

                result.add(q);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse câu hỏi từ AI: " + e.getMessage());
        }
    }

    // Xử lý JSON từ AI bền bỉ hơn
    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.trim();
        
        // Tìm vị trí đầu tiên của { hoặc [
        int firstBrace = cleaned.indexOf('{');
        int firstBracket = cleaned.indexOf('[');
        int start = -1;
        
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            start = firstBrace;
        } else if (firstBracket != -1) {
            start = firstBracket;
        }
        
        if (start != -1) {
            // Tìm vị trí cuối cùng của } hoặc ]
            int lastBrace = cleaned.lastIndexOf('}');
            int lastBracket = cleaned.lastIndexOf(']');
            int end = Math.max(lastBrace, lastBracket);
            
            if (end > start) {
                return cleaned.substring(start, end + 1);
            }
        }
        
        return cleaned.replaceAll("```json", "")
                      .replaceAll("```", "")
                      .trim();
    }

    // ===== SINH LỜI CHÀO DASHBOARD =====
    public AiGreetingResponse getGreeting(AiGreetingRequest request) {
        String prompt = buildGreetingPrompt(request);
        String raw = callGemini(prompt);
        return parseGreetingResponse(raw);
    }

    private String buildGreetingPrompt(AiGreetingRequest request) {
        String lang = (request.getLanguage() != null && request.getLanguage().startsWith("en")) ? "English" : "Vietnamese";
        
        return """
            Bạn là SynDe — người bạn nhỏ thân thiết của giáo viên, không phải trợ lý công việc.
            Tên: %s | Ngôn ngữ: %s | Thời gian: %s | Đang ở trang: %s
            
            Nhiệm vụ: Tạo 1 lời chào ngắn, ấm áp, tự nhiên như tin nhắn từ người bạn thực sự quan tâm.
            
            Tone theo thời gian:
            - 5h-9h: Tươi tắn, tiếp thêm năng lượng buổi sáng
            - 9h-12h: Nhẹ nhàng, đồng hành
            - 12h-14h: Nhắc nghỉ ngơi, ăn trưa
            - 14h-18h: Động lực nhẹ cho buổi chiều
            - 18h-22h: Ấm áp buổi tối, hỏi thăm
            - 22h+: Lo lắng nhẹ vì làm việc khuya, nhắc giữ sức khỏe
            
            Quy tắc bắt buộc (MANDATORY):
            - Xưng "Em", gọi "Thầy/Cô" kèm theo tên giáo viên.
            - Title: 4-6 từ, có cảm xúc thật.
            - Subtitle: 10-18 từ, cụ thể theo thời điểm, không sáo rỗng.
            - 1-2 emoji phù hợp cảm xúc, không spam.
            - Tuyệt đối không dùng: "Em luôn ở đây", "Hãy để em giúp", "Chúc một ngày tốt lành".
            - Đôi khi có thể đề cập nhẹ đến trang đang xem nhưng không gượng gạo.
            
            Trả về JSON duy nhất:
            {
              "title": "Tiêu đề (Vd: Thầy Dii mặc ấm nha! ❄️)",
              "subtitle": "Lời chào (Vd: Sáng nay trời hơi lạnh, Thầy nhớ quàng khăn trước khi lên lớp nhé. Em đợi Thầy ở đây ạ.)"
            }
            """.formatted(
                request.getPathname(),
                request.getFullName(),
                lang,
                new Date().toString(),
                request.getFullName()
            );
    }

    private AiGreetingResponse parseGreetingResponse(String raw) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode node = objectMapper.readTree(cleaned);
            return new AiGreetingResponse(
                node.path("title").asText("Chào mừng trở lại!"),
                node.path("subtitle").asText("Hôm nay Thầy/Cô thế nào ạ?")
            );
        } catch (Exception e) {
            return new AiGreetingResponse("Chào mừng trở lại!", "Hệ thống đã sẵn sàng hỗ trợ Thầy/Cô.");
        }
    }
}
