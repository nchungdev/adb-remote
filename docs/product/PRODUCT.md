# Product Specification (`PRODUCT.md`)

## 1. Bài toán thực tế

Trên Android 11+, Google bổ sung tính năng **Wireless Debugging (Gỡ lỗi không dây)** nhưng tồn tại 3 rào cản lớn khi điều khiển từ xa qua máy chủ cố định (như Mac mini ở nhà):

1. **Cổng ngẫu nhiên liên tục thay đổi:** Mỗi lần bật lại Wi-Fi hoặc đổi mạng, Android cấp lại cổng TCP ngẫu nhiên (`35000–49999`).
2. **NAT hai chiều khi khác mạng:** Khi điện thoại dùng 4G/5G hoặc Wi-Fi công cộng, máy Mac mini ở nhà không thể chủ động `adb connect <IP_ĐIỆN_THOẠI>` xuyên qua tường lửa nhà mạng.
3. **Thao tác CLI phức tạp trên Termux:** Cài đặt thủ công `android-tools`, `websocat`, gõ `adb pair`, `adb connect` trên màn hình dòng lệnh gây mất thời gian và dễ đứt kết nối khi chuyển vùng mạng.

---

## 2. Giải pháp của ADB Agent

**ADB Agent** đóng gói sẵn môi trường thực thi POSIX/Termux thu gọn bên trong một ứng dụng Android Native duy nhất:

1. **Tự động bắt cổng nội bộ (mDNS Loopback):** Dùng `NsdManager` phát hiện ngay cổng `_adb-tls-pairing._tcp` và `_adb-tls-connect._tcp` trên chính điện thoại.
2. **Ghép đôi 1 lần duy nhất:** Người dùng chỉ cần nhập mã ghép nối 6 chữ số ở lần đầu tiên; khóa RSA ADB được lưu bền vững trong sandbox ứng dụng.
3. **Đường hầm P2P WebRTC trực tiếp:** Điện thoại và máy chủ thiết lập kết nối ngang hàng trực tiếp (P2P DataChannel), không cần mở cổng inbound trên router và không qua trung gian bên thứ ba.
4. **Tự phục hồi khi đổi mạng:** Khi chuyển từ Wi-Fi sang 4G/5G hoặc đổi điểm phát Wi-Fi, dịch vụ chạy nền tự động kết nối lại cổng ADB nội bộ mới và khôi phục đường hầm P2P mà người dùng không cần chạm tay.

---

## 3. Ranh giới Bảo mật (Non-Goals & Security Invariants)

- **Không mở cổng Inbound trên Máy chủ / Router:** Kết nối P2P qua ICE/STUN và DTLS 1.3 mã hóa đầu cuối.
- **Không phụ thuộc VPN/Tunnel bên thứ ba:** Hoạt động độc lập không cần Cloudflare WARP hay Tailscale.
- **Không yêu cầu Root:** Hoạt động 100% dựa trên API chuẩn của Android 11+ (API 30+) và tương thích ngược xuống API 26 cho chế độ ADB TCP `5555`.
- **Không thu thập Telemetry:** Không có SDK phân tích hay quảng cáo của bên thứ ba.
