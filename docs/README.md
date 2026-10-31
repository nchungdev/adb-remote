# Documentation Index (`adb-agent`)

Tài liệu chính thức của hệ thống **ADB Agent** (`app.duinch.adbagent`).

---

## Product — `product/`
- [`PRODUCT.md`](product/PRODUCT.md): Tầm nhìn sản phẩm, bài toán giải quyết, quy tắc nghiệp vụ & bảo mật.
- [`UX.md`](product/UX.md): Thiết kế luồng trải nghiệm 1-Click Pairing, trạng thái kết nối & Terminal Drawer.
- [`ROADMAP.md`](product/ROADMAP.md): Lộ trình các giai đoạn phát triển (P0 → P4).

## Engineering — `engineering/`
- [`ARCHITECTURE.md`](engineering/ARCHITECTURE.md): Kiến trúc Clean Architecture + Feature-First + MVI & luồng dữ liệu.
- [`PROJECT_STRUCTURE.md`](engineering/PROJECT_STRUCTURE.md): Sơ đồ cây thư mục chuẩn và ranh giới từng package.
- [`CODING_RULES.md`](engineering/CODING_RULES.md): Bất biến phân tầng, SOLID, giới hạn kích thước tệp, quy tắc an toàn.
- [`CONVENTIONS.md`](engineering/CONVENTIONS.md): Quy ước đặt tên MVI (`*UiState`, `*UiIntent`, `*UiEffect`), UseCase, Git.
- [`TESTING.md`](engineering/TESTING.md): Chiến lược kiểm thử Unit Test, Fake Gateways & kiểm chứng thiết bị.
- [`CI_CD.md`](engineering/CI_CD.md): Quy trình kiểm tra tự động, quản lý phiên bản và đóng gói APK.

## Architecture Decision Records — `adr/`
- [`0001-embedded-termux-and-wireless-adb.md`](adr/0001-embedded-termux-and-wireless-adb.md): Nhúng môi trường Termux Bootstrap & tự động dò cổng mDNS Wireless ADB.
- [`0002-cloudflare-reverse-websocket-tunnel.md`](adr/0002-cloudflare-reverse-websocket-tunnel.md): [Đã thay thế] Giao thức Reverse WebSocket Tunnel qua Cloudflare Zero Trust.
- [`0003-p2p-webrtc-datachannel-tunnel.md`](adr/0003-p2p-webrtc-datachannel-tunnel.md): Đường hầm P2P WebRTC DataChannel trực tiếp (Zero Port Forwarding).
- [`0004-embedded-wireguard-tunnel.md`](adr/0004-embedded-wireguard-tunnel.md): Đường hầm Nhúng WireGuard tự code (Zero 3rd-Party App).
