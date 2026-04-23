package com.examify.examify.backend.controller;

import com.examify.examify.backend.dto.ai.*;
import com.examify.examify.backend.dto.exam.QuestionRequest;
import com.examify.examify.backend.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final GeminiService geminiService;

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody AnalyzeRequest request) {
        ValidateResponse response = geminiService.validate(request);
        if (!response.isValid()) {
            return ResponseEntity.badRequest().body(Map.of("message", response.getReason()));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody AnalyzeRequest request) {
        return ResponseEntity.ok(geminiService.analyze(request));
    }

    @PostMapping("/analyze-file")
    public ResponseEntity<AnalyzeResponse> analyzeFile(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(geminiService.analyzeFile(file));
    }

    /**
     * Official endpoint to generate exam content using AI (includes Validation + Generation)
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateAiResponse> generate(@RequestBody GenerateRequest request) {
        return ResponseEntity.ok(geminiService.generate(request));
    }

    @PostMapping("/greeting")
    public ResponseEntity<AiGreetingResponse> getGreeting(@RequestBody AiGreetingRequest request) {
        return ResponseEntity.ok(geminiService.getGreeting(request));
    }
}
