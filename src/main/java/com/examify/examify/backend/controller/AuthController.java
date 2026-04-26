package com.examify.examify.backend.controller;

import com.examify.examify.backend.dto.AuthResponse;
import com.examify.examify.backend.dto.LoginRequest;
import com.examify.examify.backend.dto.ProfileUpdateRequest;
import com.examify.examify.backend.dto.RegisterRequest;
import com.examify.examify.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PatchMapping("/profile")
    public ResponseEntity<AuthResponse> updateProfile(@RequestParam String email, @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(authService.updateProfile(email, request));
    }

    @GetMapping("/profile")
    public ResponseEntity<AuthResponse> getProfile(@RequestParam String email) {
        return ResponseEntity.ok(authService.getProfile(email));
    }
}