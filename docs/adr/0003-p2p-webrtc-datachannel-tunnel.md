# ADR 0003: Đường hầm P2P WebRTC DataChannel (Zero Port Forwarding)

- **Trạng thái:** Approved
- **Ngày:** 2026-10-31
- **Thay thế:** [ADR 0002](0002-cloudflare-reverse-websocket-tunnel.md)

## Bối cảnh

Trước đây hệ thống sử dụng Reverse WebSocket đi qua Cloudflare Tunnel (`example.trycloudflare.com`) kèm Cloudflare Access Service Token. Mô hình này đòi hỏi phụ thuộc hạ tầng Cloudflare, cấu hình Service Token/Access, và gặp giới hạn độ trễ trung gian.

## Quyết định

1. **P2P WebRTC DataChannel thuần túy:**
   - Client Android và máy chủ Go kết nối trực tiếp với nhau thông qua WebRTC DataChannel nhị phân.
   - Sử dụng STUN công khai (`stun:stun.l.google.com:19302`) kết hợp mDNS để tự động đục lỗ NAT (ICE/STUN), không cần mở cổng inbound trên router và không phụ thuộc dịch vụ bên thứ 3 (Cloudflare WARP/Tunnel, Tailscale).
2. **Kênh Báo hiệu (Signaling):**
   - Trao đổi SDP Offer/Answer và ICE Candidate qua API HTTP Signaling đơn giản (`/api/webrtc/signal`).
3. **Mã hóa và Hiệu năng:**
   - Mã hóa đầu cuối tiêu chuẩn DTLS 1.3 / SCTP, độ trễ thấp và băng thông tối ưu cho luồng dữ liệu nhị phân ADB.
