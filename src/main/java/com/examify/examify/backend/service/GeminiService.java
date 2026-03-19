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
import org.springframework.http.*;
import java.util.*;
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

    // ===== BƯỚC 2: Phân tích nội dung =====
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String prompt = buildAnalyzePrompt(request);
        String raw = callGemini(prompt);
        return parseAnalyzeResponse(raw);
    }

    // ===== BƯỚC 4: Sinh câu hỏi =====
    public List<QuestionRequest> generate(GenerateRequest request) {
        String prompt = buildGeneratePrompt(request);
        String raw = callGemini(prompt);
        return parseGenerateResponse(raw);
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
                return root.path("candidates").get(0)
                           .path("content").path("parts").get(0)
                           .path("text").asText();
            } catch (Exception e) {
                // Nếu lỗi do hết quota (429) hoặc key không hợp lệ (401), thử key tiếp theo
                if (e.getMessage().contains("429") || e.getMessage().contains("401")) {
                    currentKeyIndex = (currentKeyIndex + 1) % keys.size();
                    attempts++;
                    continue;
                }
                throw new RuntimeException("Lỗi khi gọi Gemini API: " + e.getMessage());
            }
        }
        throw new RuntimeException("Tất cả API Key đều hết hạn hoặc không khả dụng.");
    }

    // ===== PROMPT PHÂN TÍCH =====
    private String buildAnalyzePrompt(AnalyzeRequest request) {
        String typeDesc = "topic".equals(request.getInputType()) ? "chủ đề" : "tài liệu văn bản";
        String langName = "en".equals(request.getLanguage()) ? "English" : "Vietnamese";

        return """
            Bạn là chuyên gia giáo dục Việt Nam. Nhiệm vụ của bạn là phân tích %s sau đây bằng ngôn ngữ %s.
            
            Nội dung:
            %s
            
            ===== BƯỚC 1: KIỂM TRA TÍNH PHÙ HỢP VÀ ĐẦY ĐỦ (QUAN TRỌNG NHẤT) =====
            Bạn PHẢI trả lời isSufficient = false nếu:
            - Nội dung KHÔNG mang tính giáo dục: Lời chào ("Chào", "Hi"), giới thiệu ("Tôi tên là...", "Duy đây"), tán gẫu, linh tinh, đùa cợt.
            - Nội dung QUÁ NGẮN hoặc CHIẾM DỤNG: Chỉ có môn học ("Toán học"), hoặc vài từ vô nghĩa ("abc", "vcl", "123").
            - Nội dung KHÔNG ĐỦ DỮ LIỆU: Không thể tạo ít nhất 5 câu hỏi từ thông tin này mà không tự bịa thêm kiến thức.
            
            Bạn CHỈ trả lời isSufficient = true nếu:
            - Đây là một bài học, chủ đề học thuật rõ ràng có kiến thức cụ thể.
            
            QUY TẮC CƠ BẢN:
            - TUYỆT ĐỐI không bao giờ tự bịa ra kiến thức nếu input không có.
            - Nếu không chắc chắn, hãy chọn isSufficient = false.
            - Nếu isSufficient = false, hãy viết message giải thích: "Nội dung mang tính chất chào hỏi/linh tinh, vui lòng cung cấp tài liệu hoặc chủ đề học tập cụ thể."
            
            ===== BƯỚC 2: GỢI Ý SỐ CÂU HỎI =====
            Nếu isSufficient = true, gợi ý số câu phù hợp với độ dài/phức tạp của nội dung.
            Đảm bảo: suggestedMultipleChoice + suggestedMultipleAnswer + suggestedEssay = suggestedTotal
            
            ===== QUY TẮC NGÔN NGỮ =====
            Toàn bộ các field: message, suggestedTitle, suggestedDescription, detectedTopics, summary
            đều phải viết bằng ngôn ngữ: %s
            
            ===== ĐỊNH DẠNG OUTPUT =====
            Trả về JSON hợp lệ (tuyệt đối không có markdown, không có ```json, không có ``` ):
            {
              "isSufficient": true,
              "message": "Nội dung đã đủ để tạo câu hỏi. (Nếu false: hướng dẫn cụ thể người dùng cần bổ sung gì)",
              "suggestedTitle": "Tên đề thi ngắn gọn, súc tích",
              "suggestedDescription": "Mô tả 1-2 câu về nội dung đề thi",
              "suggestedTotal": 20,
              "suggestedMultipleChoice": 12,
              "suggestedMultipleAnswer": 5,
              "suggestedEssay": 3,
              "detectedTopics": ["Chủ đề 1", "Chủ đề 2", "Chủ đề 3"],
              "summary": "Tóm tắt ngắn gọn nội dung trong 2-3 câu"
            }
            """.formatted(typeDesc, langName, request.getContent(), langName);
    }

    // ===== PROMPT SINH CÂU HỎI =====
    private String buildGeneratePrompt(GenerateRequest request) {
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
        if (!"mixed".equals(request.getDifficulty())) {
            return "Độ khó: " + request.getDifficulty();
        }
        return "Độ khó hỗn hợp: Dễ %d%%, Trung bình %d%%, Khó %d%%"
            .formatted(request.getEasyPercent(),
                       request.getMediumPercent(),
                       request.getHardPercent());
    }

    // ===== PARSE RESPONSE =====
    private AnalyzeResponse parseAnalyzeResponse(String raw) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode node = objectMapper.readTree(cleaned);
            List<String> topics = new ArrayList<>();
            node.path("detectedTopics").forEach(t -> topics.add(t.asText()));
            
            AnalyzeResponse response = new AnalyzeResponse(
                node.path("suggestedTotal").asInt(10),
                node.path("suggestedMultipleChoice").asInt(6),
                node.path("suggestedMultipleAnswer").asInt(3),
                node.path("suggestedEssay").asInt(1),
                topics,
                node.path("summary").asText("")
            );
            
            response.setSufficient(node.path("isSufficient").asBoolean(false));
            response.setMessage(node.path("message").asText(""));
            response.setSuggestedTitle(node.path("suggestedTitle").asText(""));
            response.setSuggestedDescription(node.path("suggestedDescription").asText(""));
            
            return response;
        } catch (Exception e) {
            // Fallback nếu parse lỗi
            AnalyzeResponse fallback = new AnalyzeResponse(0, 0, 0, 0, List.of(), "Lỗi phân tích nội dung.");
            fallback.setSufficient(false);
            return fallback;
        }
    }

    private List<QuestionRequest> parseGenerateResponse(String raw) {
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

    // Xóa markdown code block nếu Gemini trả về
    private String cleanJson(String raw) {
        return raw.replaceAll("```json", "")
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
