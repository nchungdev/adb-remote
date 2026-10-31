# ADR 0002: Đường hầm Đảo chiều WebSocket qua Cloudflare Tunnel (`adb.duinch.app`)

- **Trạng thái:** Superseded bởi [ADR 0003](0003-p2p-webrtc-datachannel-tunnel.md)
- **Ngày:** 2026-10-31

## Bối cảnh

Khi điện thoại di chuyển ngoài nhà (dùng 4G/5G hoặc Wi-Fi khác mạng), địa chỉ IP của điện thoại nằm sau CGNAT của nhà mạng và cổng Wireless Debugging liên tục đổi ngẫu nhiên. Đồng thời, máy chủ Mac mini ở nhà **tuyệt đối không mở cổng SSH (22) hay cổng inbound** ra Internet để đảm bảo an toàn bảo mật.

## Quyết định

1. **Outbound-Only Reverse WebSocket (`WSS` qua cổng 443):**
   - Ứng dụng trên điện thoại chủ động mở kết nối WebSocket bảo mật (`wss://adb.duinch.app/api/tunnel`) đi qua Cloudflare Tunnel sẵn có của Mac mini.
   - Khi kết nối thiết lập, dữ liệu nhị phân giữa socket ADB cục bộ trên điện thoại (`127.0.0.1:<connectPort>`) và máy chủ Mac mini được chuyển tiếp hai chiều qua khung nhị phân WebSocket.
2. **Tương thích Cloudflare Zero Trust Access:**
   - Cho phép đính kèm cặp header `CF-Access-Client-Id` và `CF-Access-Client-Secret` (Service Token) ngay trong bắt tay nâng cấp WebSocket để đi qua lớp xác thực Zero Trust mà không cần mở trình duyệt nhập OTP thủ công khi chạy ngầm.
3. **Tự động Phục hồi theo Mạng (`ConnectivityManager` + Exponential Backoff):**
   - Dịch vụ nền theo dõi sự kiện thay đổi mạng (`Wi-Fi` ↔ `Cellular`). Ngay khi có mạng mới và mDNS cập nhật cổng `_adb-tls-connect._tcp`, đường hầm tự động kết nối lại với Jitter Backoff (`1s -> 2s -> 4s ... tối đa 30s`).

## Hệ quả

- Không cần mở bất kỳ port nào trên router gia đình hay Mac mini.
- Chuyển vùng mạng (Wi-Fi nhà -> 4G ngoài đường -> Wi-Fi văn phòng) tự động khôi phục phiên điều khiển mà không cần thao tác tay.
