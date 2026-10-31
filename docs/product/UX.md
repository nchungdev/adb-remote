# UX & Interaction Specification (`UX.md`)

## 1. Triết lý Giao diện

- **Zero CLI Friction:** Người dùng phổ thông điều khiển mọi thứ bằng nút bấm trực quan trên 1 màn hình chính duy nhất (**Single-Screen Dashboard**).
- **Power-User Escape Hatch:** Vẫn cung cấp ngăn kéo **Embedded Termux Console** (Bottom Sheet) để chạy lệnh chẩn đoán trực tiếp khi cần.
- **Dark-First Technical Aesthetic:** Giao diện tối chuẩn kỹ thuật (Slate/Cyan palette đồng bộ với web dashboard `adb.duinch.app`), độ tương phản cao, hỗ trợ Dynamic Type.

---

## 2. Cấu trúc Màn hình Chính (`DashboardScreen`)

1. **Hero Status Banner (Trạng thái Đường hầm):**
   - Hiển thị trạng thái thời gian thực: `Disconnected` (Xám), `Connecting` / `Reconnecting` (Vàng), `Connected` (Xanh ngọc), `Error` (Đỏ).
   - Hiển thị loại mạng hiện tại (`Wi-Fi`, `Cellular 4G/5G`) và cổng ADB nội bộ đã gắn.
   - Nút hành động chính (Primary CTA): **"Bật kết nối về Mac mini"** / **"Ngắt kết nối"**.

2. **Thẻ Ghép đôi Wireless ADB (`PairingSection`):**
   - Trạng thái mDNS tự động: *"Đang dò cổng ghép đôi..."* hoặc *"Đã phát hiện cổng Pairing: 46653"*.
   - Nút mở nhanh **Cài đặt cho nhà phát triển** (Split-screen / Notification friendly).
   - Ô nhập mã 6 chữ số (chỉ nhận số, tự kích hoạt nút **Ghép đôi** khi đủ 6 số).

3. **Thẻ Cấu hình Cloudflare Tunnel:**
   - Endpoint URL (mặc định `wss://adb.duinch.app/api/tunnel`).
   - Tên định danh thiết bị (`Device Alias`, ví dụ `Galaxy-S24-Plus`).
   - Tuỳ chọn nâng cao (ẩn/hiện): `CF-Access-Client-Id` & `CF-Access-Client-Secret` (ẩn ký tự nhạy cảm).
   - Công tắc: *"Tự động kết nối lại khi đổi mạng Wi-Fi / 4G"*.

4. **Embedded Termux Console Sheet:**
   - Mở bằng nút **Terminal** trên thanh TopAppBar.
   - Hiển thị trạng thái Bootstrap (`Ready` / `Extracting`), log phiên và ô nhập lệnh nhanh (`adb devices`, `getprop`,...).
