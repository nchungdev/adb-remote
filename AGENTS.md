# Agent Instructions (`adb-agent`)

Đọc [`README.md`](README.md), [`docs/product/PRODUCT.md`](docs/product/PRODUCT.md), [`docs/engineering/ARCHITECTURE.md`](docs/engineering/ARCHITECTURE.md), [`docs/engineering/PROJECT_STRUCTURE.md`](docs/engineering/PROJECT_STRUCTURE.md) và [`docs/engineering/CODING_RULES.md`](docs/engineering/CODING_RULES.md) trước khi chỉnh sửa mã nguồn. Luôn đọc nội dung mới nhất của tệp trước khi sửa.

---

## 1. Nguyên tắc Bảo mật & Sản phẩm Bất biến

1. **Zero Inbound Ports:** Hệ thống chỉ sử dụng kết nối **Outbound HTTPS/WSS (cổng 443)** từ điện thoại về Cloudflare Tunnel (`adb.duinch.app`). Tuyệt đối không yêu cầu mở cổng SSH (22) hay cổng TCP thô trên Mac mini hoặc router.
2. **Bảo mật Bí mật (Secrets Hygiene):** Không bao giờ commit `local.properties`, keystore, Cloudflare Tunnel Token, `CF-Access-Client-Secret`, khóa RSA ADB cá nhân (`adbkey`) hoặc dữ liệu thiết bị thật lên Git.
3. **App Sandbox Execution:** Môi trường Embedded Termux chỉ chạy bên trong thư mục riêng của ứng dụng (`Context.filesDir/usr` và `Context.filesDir/home`), không yêu cầu quyền Root.

---

## 2. Kiến trúc Bắt buộc: Clean Architecture + Feature-First + MVI

Mọi tính năng nằm dưới `app/src/main/kotlin/app/duinch/adbagent/features/<feature_name>/` và phân tách nghiêm ngặt thành 4 tầng:

1. **`domain/` (Pure Kotlin 100%)**
   - Chứa Entities, Value Objects, Domain Events/Errors và Gateway/Repository Interfaces.
   - **CẤM TUYỆT ĐỐI** import `android.*`, `androidx.*`, `okhttp3.*`, `kotlinx.coroutines.android.*`, hoặc bất kỳ thư viện UI/IO cụ thể nào.
2. **`application/` (Pure Kotlin Use Cases)**
   - Chứa các `*UseCase` điều phối nghiệp vụ, kiểm tra điều kiện đầu vào, chính sách retry/backoff.
   - Chỉ phụ thuộc vào `domain/` (và `core/model` thuần Kotlin). **CẤM** import `android.*` hoặc `androidx.compose.*`.
3. **`data/` (Adapters & Infrastructure)**
   - Triển khai các interface của `domain/`: `NsdManager` mDNS discovery, `ProcessBuilder` cho Embedded Termux runtime, OkHttp/WebSocket tunnel bridge, `SharedPreferences` / storage, `ForegroundService`.
4. **`presentation/` (Strict MVI + Jetpack Compose)**
   - Định nghĩa hợp đồng MVI rõ ràng trong `*Contract.kt`:
     - `*UiState` (`@Immutable` / `data class` bất biến)
     - `*UiIntent` (`sealed interface` đại diện hành động của người dùng)
     - `*UiEffect` (`sealed interface` cho sự kiện một lần: Toast/Snackbar, mở cài đặt hệ thống)
   - `@Composable` chỉ nhận `(state: *UiState, onIntent: (*UiIntent) -> Unit)` — **CẤM** đặt logic nghiệp vụ, gọi mạng, hay biến đổi dữ liệu phức tạp trực tiếp trong `@Composable`.

---

## 3. Giới hạn Kích thước & Chất lượng Mã nguồn

- **Giới hạn tệp:** Mọi tệp nguồn viết tay (`.kt`, `.py`) **không vượt quá 350 dòng**. Tách nhỏ thành sub-components hoặc use case chuyên biệt khi vượt ngưỡng 300 dòng.
- **SOLID, KISS, YAGNI:** Không tạo các lớp trừu tượng thừa thãi không có nhu cầu thực tế; ưu tiên composition hơn inheritance.
- **Tìm kiếm nhanh:** Ưu tiên `rg -n "pattern"` thay vì `grep -r`.

---

## 4. Cổng Kiểm tra Bắt buộc (Verification Gate)

Trước khi hoàn tất tác vụ hoặc tạo commit/PR, bắt buộc chạy và đảm bảo PASS toàn bộ:

```bash
python3 scripts/check_project.py
./gradlew testDebugUnitTest assembleDebug
```

Không đánh dấu hoàn thành chỉ dựa trên việc viết xong file nguồn mà chưa chạy kiểm tra kiến trúc và biên dịch thực tế.
