package com.examify.examify.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt lỗi validation (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("message", "Dữ liệu không hợp lệ");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    // Bắt lỗi runtime (email đã tồn tại, sai mật khẩu,...)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("message", ex.getMessage());

        return ResponseEntity.badRequest().body(response);
    }
}