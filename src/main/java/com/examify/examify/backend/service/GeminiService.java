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
        if ("topic".equals(request.getInputType())) {
            return """
                Bạn là chuyên gia giáo dục. Phân tích chủ đề sau và gợi ý số câu hỏi phù hợp.
                Chủ đề: %s
                
                Trả về JSON (không có markdown, không có ```):
                {
                  "suggestedTotal": 20,
                  "suggestedMultipleChoice": 12,
                  "suggestedMultipleAnswer": 5,
                  "suggestedEssay": 3,
                  "detectedTopics": ["topic1", "topic2"],
                  "summary": "Mô tả ngắn về chủ đề"
                }
                """.formatted(request.getContent());
        }

        return """
            Bạn là chuyên gia giáo dục. Phân tích tài liệu sau và gợi ý số câu hỏi phù hợp.
            Tài liệu dài %d từ.
            
            Nội dung:
            %s
            
            Trả về JSON (không có markdown, không có ```):
            {
              "suggestedTotal": số_câu_phù_hợp,
              "suggestedMultipleChoice": số_câu_tn_1_đáp_án,
              "suggestedMultipleAnswer": số_câu_tn_nhiều_đáp_án,
              "suggestedEssay": số_câu_tự_luận,
              "detectedTopics": ["chủ_đề_1", "chủ_đề_2"],
              "summary": "tóm_tắt_ngắn_tài_liệu"
            }
            """.formatted(
                request.getContent().split("\\s+").length,
                request.getContent()
            );
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
                "explanation": "Giải thích đáp án",
                "difficulty": "medium",
                "topic": "chủ đề con",
                "tags": ["tag1", "tag2"]
              }
            ]
            
            Lưu ý:
            - multiple_choice: correctAnswers chỉ có 1 phần tử
            - multiple_answer: correctAnswers có 2+ phần tử
            - essay: choices = [], correctAnswers = [], thêm field sampleAnswer
            - Câu hỏi phải rõ ràng, chính xác, không trùng lặp
            """.formatted(
                request.getContent(),
                request.getMultipleChoice(),
                request.getMultipleAnswer(),
                request.getEssay(),
                difficultyNote
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
            return new AnalyzeResponse(
                node.path("suggestedTotal").asInt(10),
                node.path("suggestedMultipleChoice").asInt(6),
                node.path("suggestedMultipleAnswer").asInt(3),
                node.path("suggestedEssay").asInt(1),
                topics,
                node.path("summary").asText("")
            );
        } catch (Exception e) {
            // Fallback nếu parse lỗi
            return new AnalyzeResponse(10, 6, 3, 1, List.of(), "Không thể phân tích");
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
}
