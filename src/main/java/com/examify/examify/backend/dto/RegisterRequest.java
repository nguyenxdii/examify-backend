package com.examify.examify.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 50, message = "Mật khẩu phải từ 6 đến 50 ký tự")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S+$",
            message = "Mật khẩu phải có ít nhất 1 chữ cái, 1 số và không chứa khoảng trắng"
    )
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    @Pattern(
            regexp = "^\\S.*\\S$|^\\S$",
            message = "Họ tên không được bắt đầu hoặc kết thúc bằng khoảng trắng"
    )
    private String fullName;

    @NotBlank(message = "Trường công tác không được để trống")
    @Size(max = 200, message = "Tên trường không được quá 200 ký tự")
    private String school;

    @NotBlank(message = "Lĩnh vực dạy không được để trống")
    private String field;
}