# ADB Remote & Manager (`adb-remote`)

Hệ thống điều khiển và duy trì đường hầm ADB không dây từ xa & nội bộ toàn diện, bao gồm ứng dụng di động Android (`app/`) và máy chủ quản trị đa nền tảng viết bằng Go (`server/`).

Hệ thống hoạt động theo cơ chế **P2P WebRTC DataChannel trực tiếp** (kết hợp mDNS cho mạng LAN nội bộ và Google Public STUN cho Internet), **không cần mở bất kỳ cổng inbound nào trên router**, và **không phụ thuộc vào bất kỳ ứng dụng bên thứ 3 nào (như Cloudflare WARP hay Tailscale)**.

---

## Cấu trúc Monorepo

```text
adb-agent/
├── app/                        # Ứng dụng Android Client (Kotlin, Jetpack Compose, WebRTC)
│   ├── src/                    # Clean Architecture 4 tầng + Feature-First + MVI
│   ├── gradle/                 # Gradle Wrapper độc lập
│   ├── build.gradle.kts        # Single-module Android application build script
│   ├── settings.gradle.kts     # Cấu hình dự án Android độc lập
│   └── version.properties      # Quản lý phiên bản APK & Semantic Versioning
│
├── server/                     # Máy chủ Go Server & Cross-Platform Packaging
│   ├── cmd/api/main.go         # Điểm khởi chạy daemon server (HTTP, WebSocket, WebRTC)
│   ├── internal/               # Core domain, WebRTC Hub, ADB controller, Web UI
│   ├── packaging/              # LaunchAgent (macOS), Systemd (Linux), VBS/Batch (Windows)
│   ├── scripts/package.sh      # Script đóng gói phân phối 6 targets tự động
│   ├── dist/                   # File cài đặt nén (.tar.gz, .zip) & SHA256 checksums
│   ├── go.mod / go.sum         # Quản lý module Go
│   └── run.sh                  # Script khởi chạy nhanh máy chủ
│
├── docs/                       # Tài liệu kiến trúc, UX và quy chuẩn lập trình
└── scripts/                    # Bộ kiểm tra kiến trúc tự động (check_project.py)
```

---

## 1. Ứng dụng Android (`app/`)

- **1-Click Wireless ADB Pairing:** Tự động dò cổng `_adb-tls-pairing._tcp` và `_adb-tls-connect._tcp` qua Android mDNS (`NsdManager`), ghép đôi bằng mã 6 chữ số ngay trên thông báo hoặc màn hình chính.
- **Embedded Termux Prefix Runtime:** Đóng gói môi trường POSIX/Termux tối giản (`$PREFIX/bin/adb`, shell runtime) chạy cô lập trong sandbox riêng của ứng dụng (`filesDir/usr`), kèm ngăn kéo Termux Console cho chẩn đoán nâng cao.
- **P2P WebRTC Direct DataChannel:** Sử dụng WebRTC DataChannel truyền nhận nhị phân trực tiếp với máy tính qua STUN NAT Traversal (`stun:stun.l.google.com:19302`), độ trễ cực thấp và bảo mật mã hóa end-to-end DTLS/SCTP.
- **Network Roaming Resilience:** Tự động khôi phục kết nối và bắt tay WebRTC lại qua Exponential Backoff + Jitter mỗi khi điện thoại chuyển đổi mạng Wi-Fi hoặc 4G/5G.
- **Kiến trúc:** Clean Architecture + Feature-First + MVI (Kotlin + Jetpack Compose Material 3), mỗi file tuân thủ nghiêm ngặt giới hạn $\le 350$ dòng code.

### Khởi chạy & Kiểm thử Android:
```bash
cd app

# Kiểm tra ranh giới Clean Architecture + MVI & giới hạn <= 350 dòng/file
python3 ../scripts/check_project.py

# Chạy Unit Tests
./gradlew testDebugUnitTest

# Đóng gói APK Debug
./gradlew assembleDebug
```

---

## 2. Máy chủ Go Server (`server/`)

- **Thuần Go 100% (Zero CGO):** Tích hợp WebRTC native qua thư viện `pion/webrtc/v4`, chạy trên mọi hệ điều hành không phụ thuộc thư viện C.
- **Giao diện Web UI hiện đại:** Bảng điều khiển thời gian thực theo dõi danh sách thiết bị kết nối, trạng thái P2P WebRTC Direct, IP nội bộ, thời gian hoạt động và cấu hình.
- **Tự động gắn kết ADB (`adb connect`):** Khi thiết bị mở DataChannel, server tự động bind cổng local loopback (`127.0.0.1:port`) và chạy lệnh `adb connect` để IDE (Android Studio, VS Code) nhận diện ngay lập tức.
- **Đóng gói phân phối đa nền tảng:**
  - **macOS:** `tar.gz` kèm `install.sh` / `uninstall.sh` tự tạo `LaunchAgent` chạy nền khi khởi động máy.
  - **Linux:** `tar.gz` kèm systemd user service `adb-device-manager.service`.
  - **Windows:** `.zip` kèm script VBS chạy nền ẩn và shortcut tự khởi chạy khi đăng nhập Windows.

### Khởi chạy & Đóng gói Server:
```bash
cd server

# Chạy Unit Tests với race detector
go test -v -race ./...

# Chạy server ở chế độ phát triển
./run.sh

# Đóng gói cho macOS, Linux, Windows (6 binaries)
./scripts/package.sh
```

---

## Giấy phép
Phát hành theo giấy phép [MIT](LICENSE).
