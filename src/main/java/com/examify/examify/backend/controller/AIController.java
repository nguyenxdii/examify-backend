package com.examify.examify.backend.controller;

import com.examify.examify.backend.dto.ai.*;
import com.examify.examify.backend.dto.exam.QuestionRequest;
import com.examify.examify.backend.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final GeminiService geminiService;

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody AnalyzeRequest request) {
        return ResponseEntity.ok(geminiService.analyze(request));
    }

    @PostMapping("/generate")
    public ResponseEntity<List<QuestionRequest>> generate(@RequestBody GenerateRequest request) {
        return ResponseEntity.ok(geminiService.generate(request));
    }
}
