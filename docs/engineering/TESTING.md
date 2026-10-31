# Testing Strategy (`TESTING.md`)

## 1. Chiến lược Kiểm thử Đa tầng

1. **Domain & Application Unit Tests (`app/src/test`):**
   - Chạy thuần trên JVM với tốc độ mili-giây (`./gradlew testDebugUnitTest`).
   - Sử dụng **Fake Gateways** viết tay (in-memory) thay vì lạm dụng thư viện mock nặng nề, kiểm tra hành vi thực tế của Use Case:
     - Kiểm tra hợp lệ mã ghép nối 6 chữ số, cổng hợp lệ, tự động kết nối sau khi pair thành công.
     - Kiểm tra từ chối lệnh rỗng hoặc môi trường Termux chưa khởi tạo.
     - Kiểm tra lưu/đọc cấu hình URL `wss://`, bật/tắt đường hầm và công thức tính Exponential Backoff.
2. **Architecture Gate (`scripts/check_project.py`):**
   - Tự động quét toàn bộ cây thư mục để phát hiện:
     - Tệp vượt quá 350 dòng.
     - Vi phạm import `android.*` / `androidx.*` trong `domain/` hoặc `application/`.
     - Thiếu hợp đồng MVI (`UiState` / `UiIntent`).
3. **Device Verification:**
   - Biên dịch APK thực tế bằng `./gradlew assembleDebug` và kiểm tra khởi chạy trên thiết bị Android thật hoặc máy ảo khi có kết nối.
