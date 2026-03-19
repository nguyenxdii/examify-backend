package com.examify.examify.backend.service;

import com.examify.examify.backend.dto.AuthResponse;
import com.examify.examify.backend.dto.LoginRequest;
import com.examify.examify.backend.dto.ProfileUpdateRequest;
import com.examify.examify.backend.dto.RegisterRequest;
import com.examify.examify.backend.model.User;
import com.examify.examify.backend.repository.UserRepository;
import com.examify.examify.backend.util.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng");
        }

        // Tạo user mới
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setGender(request.getGender());
        user.setSchool(request.getSchool());
        user.setField(request.getField());
        user.setRole("teacher");   // mặc định đăng ký là teacher
        user.setLocked(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Tạo token và trả về
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getGender(), user.getSchool(), user.getField());
    }

    public AuthResponse login(LoginRequest request) {
        // Tìm user theo email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu không đúng"));

        // Kiểm tra tài khoản bị khóa
        if (user.isLocked()) {
            throw new RuntimeException("Tài khoản đã bị khóa");
        }

        // Kiểm tra password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Email hoặc mật khẩu không đúng");
        }

        // Tạo token và trả về
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getGender(), user.getSchool(), user.getField());
    }

    public AuthResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getSchool() != null) user.setSchool(request.getSchool());
        if (request.getField() != null) user.setField(request.getField());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getGender(), user.getSchool(), user.getField());
    }

    public AuthResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getGender(), user.getSchool(), user.getField());
    }
}