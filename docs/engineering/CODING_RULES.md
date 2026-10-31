# Coding Rules (`CODING_RULES.md`)

## 1. Ranh giới Phân tầng Nghiêm ngặt

1. **Tầng `domain` 100% Pure Kotlin:**
   - Cấm mọi câu lệnh `import android.*`, `import androidx.*`, `import okhttp3.*`, `import java.net.Socket`.
   - Các bất biến dữ liệu (ví dụ: mã ghép đôi đúng 6 chữ số, cổng TCP nằm trong khoảng `1..65535`, URL bắt đầu bằng `ws://` hoặc `wss://`) phải được kiểm chứng tại `domain` hoặc `application`.
2. **Tầng `application` không chứa Framework UI/OS:**
   - Cấm `import android.*` và `import androidx.compose.*`.
   - Mỗi Use Case đảm nhận đúng một trách nhiệm đơn lẻ (Single Responsibility Principle) với phương thức `operator fun invoke(...)` hoặc `execute(...)`.
3. **Tầng `presentation` Stateless UI:**
   - `@Composable` không khởi tạo coroutine gọi I/O trực tiếp và không chứa phép biến đổi nghiệp vụ phức tạp.
   - Trạng thái giao diện luôn là `data class` bất biến (`val`), cập nhật qua `.copy(...)` trong `ViewModel`.

---

## 2. Giới hạn Kích thước & Độ phức tạp

- **Giới hạn cứng:** Mỗi tệp `.kt` và `.py` viết tay **không vượt quá 350 dòng** (được kiểm tra tự động bởi `scripts/check_project.py`).
- **Hàm ngắn gọn:** Ưu tiên hàm dưới 50 dòng, tách các khối giao diện Compose lớn thành các `@Composable` nhỏ có tên rõ nghĩa.

---

## 3. Bảo mật & Quyền riêng tư

- Không ghi giá trị thực của `cfAccessClientSecret` hoặc mã ghép nối ra `Logcat`.
- Thực thi tiến trình con trong `LocalTermuxRuntimeAdapter` phải truyền danh sách tham số rõ ràng hoặc kiểm soát môi trường `$PREFIX`, không ghép chuỗi lệnh không kiểm soát từ nguồn bên ngoài.
