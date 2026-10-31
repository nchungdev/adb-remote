# UX & Interaction Specification (`UX.md`)

## 1. Triết lý Giao diện Mobile-First (UI/UX Pro Max)

- **Thumb-Zone Bottom Navigation (3 Tab):** Tách biệt luồng vận hành hằng ngày (**Kết nối**) khỏi luồng cài đặt lần đầu (**Ghép đôi**) và cấu hình hệ thống (**Thiết lập**) thay vì nhồi toàn bộ biểu mẫu vào một trang cuộn dài.
- **Progressive Disclosure:** Các trường nhập nâng cao (chế độ chia đôi màn hình, Service Token) mặc định thu gọn trong accordion và chỉ mở khi cần.
- **Vector-Only & Semantic Tokens:** Tuyệt đối không dùng emoji làm icon điều hướng/trạng thái; toàn bộ icon sử dụng bộ vector hình học 24dp / nét 2px đồng nhất (`AppIcons`), màu sắc lấy từ `MaterialTheme` & `AdbTheme.colors` đạt chuẩn tương phản WCAG AA (`>= 4.5:1`) cho cả Dark Mode và Light Mode.
- **Android Touch & Safe-Area Standards:** Mọi vùng chạm đạt tối thiểu `48dp` (`56dp` cho nút CTA chính), tôn trọng vùng khuyết màn hình, thanh điều hướng cử chỉ và bàn phím ảo (`imePadding`).

---

## 2. Cấu trúc 3 Tab Chính (`DashboardScreen`)

1. **Tab 1 — Kết nối (`TunnelHomeTab`):**
   - **Compact OTA Strip:** Chỉ xuất hiện khi phát hiện bản cập nhật mới hoặc đang tải.
   - **Hero Switchboard Card:** Biểu tượng khiên trạng thái chuyển màu mượt mà, dải 3 ô Bento Telemetry (`Mạng`, `Cổng ADB`, `Lưu lượng`) và nút CTA 56dp bật/ngắt đường hầm.
   - **Readiness Checklist:** Thẻ trạng thái kiểm tra nhanh *Cổng ADB nội bộ* (chạm để chuyển thẳng tới bước xử lý tương ứng).

2. **Tab 2 — Ghép đôi (`PairingSection`):**
   - Hướng dẫn 3 bước trực quan và nút chính **Bắt đầu Ghép đôi qua Thông báo** (nhập mã 6 số trực tiếp trên thanh thông báo mà không rời Cài đặt).
   - Nút kích hoạt nhanh cổng cố định `5555` cho máy đã từng ghép đôi.
   - Khung mở rộng **Nhập mã trực tiếp (Chia đôi màn hình)**.

3. **Tab 3 — Thiết lập (`TunnelSettingsTab`):**
   - Thẻ kiểm tra & cập nhật OTA tự động (`UpdateBannerCard`).
   - Thẻ mở **Embedded Termux Console** (kèm dải chip lệnh nhanh `adb devices -l`, `adb tcpip 5555`, `adb version`...).
   - Cấu hình P2P WebRTC Signaling Endpoint và Wi-Fi nhà tin cậy.
