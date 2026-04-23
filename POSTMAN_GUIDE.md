# 📘 SÁCH HƯỚNG DẪN TEST BACKEND EXAMIFY (DÀNH CHO BÁO CÁO)

Tài liệu này phản ánh chính xác 100% cấu trúc của Backend hiện tại. Sử dụng các mẫu JSON này để đảm bảo test thành công và không bị lỗi 400.

---

## 🛠 BƯỚC 0: CHUẨN BỊ MÔI TRƯỜNG
1. Chọn Environment: `examify-local`
2. Đảm bảo có biến `baseUrl` (http://localhost:8080) và `jwtToken` (trống).

---

## 🔐 CHƯƠNG 1: XÁC THỰC & TÀI KHOẢN (AUTH)

### BƯỚC 1.1: Đăng ký (Register)
```http
METHOD: POST | URL: {{baseUrl}}/api/auth/register
```
- **BODY JSON:**
```json
{
    "email": "teacher@gmail.com",
    "password": "password123",
    "fullName": "Nguyễn Văn Giáo Viên",
    "school": "Đại học Bách Khoa Hà Nội"
}
```

### BƯỚC 1.2: Đăng nhập & Lưu Token
- Dán Script này vào tab **Scripts** -> **Post-response** của request Login:
```javascript
pm.environment.set("jwtToken", pm.response.json().token);
```

---

## 📝 CHƯƠNG 2: QUẢN LÝ ĐỀ THI & CÂU HỎI (EXAMS)

### BƯỚC 2.1: Tạo đề thi mới
```http
METHOD: POST | URL: {{baseUrl}}/api/exams
```
- **BODY JSON:**
```json
{
    "title": "Đề thi thực tế - Báo cáo",
    "subject": "Công nghệ phần mềm",
    "description": "Đề thi dùng để demo báo cáo hệ thống"
}
```
- **SỬ DỤNG SCRIPT ĐỂ LƯU ID:** 
```javascript
pm.environment.set("examId", pm.response.json().id);
```

### BƯỚC 2.2: Thêm Câu hỏi (Cấu trúc chuẩn)
```http
METHOD: POST | URL: {{baseUrl}}/api/exams/{{examId}}/questions
```
- **BODY JSON (KHÔNG CÓ TRƯỜNG POINT):**
```json
{
    "content": "Theo bạn, Spring Boot là gì?",
    "type": "multiple_choice",
    "choices": [
        { "key": "A", "content": "Một Java Framework" },
        { "key": "B", "content": "Một ngôn ngữ mới" }
    ],
    "correctAnswers": ["A"],
    "explanation": "Spring Boot giúp phát triển ứng dụng Java nhanh hơn.",
    "difficulty": "medium"
}
```

---

## 🤖 CHƯƠNG 3: SINH ĐỀ AI (AI SERVICES)

### BƯỚC 3.1: AI Generate
```http
METHOD: POST | URL: {{baseUrl}}/api/ai/generate
```
- **BODY JSON:**
```json
{
    "examId": "{{examId}}",
    "content": "Các mô hình phát triển phần mềm (Agile, Waterfall)",
    "inputType": "topic",
    "multipleChoice": 3,
    "essay": 1,
    "language": "vi"
}
```

---

## 🚪 CHƯƠNG 4: PHÒNG THI & SINH VIÊN (ROOMS)

### BƯỚC 4.1: Tạo Phòng thi
```http
METHOD: POST | URL: {{baseUrl}}/api/rooms
```
- **BODY JSON:**
```json
{
    "examId": "{{examId}}",
    "name": "Phòng thi thực hành số 1",
    "mode": "exam",
    "durationMinutes": 60
}
```
- **SCRIPT LƯU ID:** `pm.environment.set("roomId", pm.response.json().id);`

---

## ⚠️ LƯU Ý QUAN TRỌNG KHI TEST
1. **Bearer Token:** Luôn chọn Tab `Authorization` -> Type `Bearer Token` và điền `{{jwtToken}}` cho tất cả Request từ Chương 2 đổ đi.
2. **Trường Null:** Các trường trả về `null` là do hiện tại Backend chưa gán dữ liệu cho chúng, đây là kết quả thực tế của hệ thống để bạn đưa vào báo cáo.
3. **HTTP Method:** Kiểm tra kỹ `POST`, `GET`, `PUT`, `PATCH`, `DELETE` trước khi nhấn Send.
