# ADB Agent (`app.duinch.adbagent`)

Ứng dụng Android chuyên dụng tích hợp môi trường **Embedded Termux Runtime** bên trong, giúp tự động ghép đôi **Wireless Debugging (ADB nội bộ)** và duy trì đường hầm đảo chiều (**Reverse WebSocket Tunnel**) an toàn qua HTTPS/WSS (`adb.duinch.app`) về máy chủ Mac mini mà **không cần mở bất kỳ port inbound nào** trên máy chủ hay router, bất chấp điện thoại chuyển đổi mạng Wi-Fi / 4G / 5G.

---

## Điểm nổi bật

- **1-Click Wireless ADB Pairing:** Tự động dò cổng `_adb-tls-pairing._tcp` và `_adb-tls-connect._tcp` qua Android mDNS (`NsdManager`), ghép đôi bằng mã 6 chữ số ngay trên thông báo hoặc màn hình chính mà không cần gõ lệnh CLI thủ công.
- **Embedded Termux Prefix Runtime:** Đóng gói môi trường POSIX/Termux tối giản (`$PREFIX/bin/adb`, shell runtime) chạy cô lập trong sandbox riêng của ứng dụng (`filesDir/usr`), kèm ngăn kéo **Termux Console** cho chẩn đoán nâng cao.
- **Zero-Inbound Reverse Tunnel (Cloudflare Tunnel Ready):** Chủ động mở kết nối outbound `WSS` (cổng 443 TLS) kèm **Cloudflare Access Service Token** (`CF-Access-Client-Id` / `CF-Access-Client-Secret`) về `adb.duinch.app`, cầu nối trực tiếp luồng ADB nội bộ về Mac mini.
- **Network Roaming Resilience:** Lắng nghe `ConnectivityManager.NetworkCallback` trong `ForegroundService` để tự động kết nối lại theo chiến lược Exponential Backoff + Jitter mỗi khi điện thoại đổi Wi-Fi hoặc chuyển sang 4G/5G.
- **Kiến trúc chuẩn mực:** **Clean Architecture + Feature-First + MVI** (Kotlin + Jetpack Compose Material 3).

---

## Kiến trúc tổng quan

Dự án tổ chức theo **Feature-First** kết hợp **Clean Architecture 4 tầng** bên trong mỗi feature và **MVI** ở tầng giao diện:

```text
app/src/main/kotlin/app/duinch/adbagent/
├── core/                       # Design system, DI container, shared value objects
└── features/
    ├── pairing/                # Dò mDNS & ghép đôi Wireless Debugging nội bộ
    │   ├── domain/             # Entities, Value Objects, Gateway interfaces (Pure Kotlin)
    │   ├── application/        # Use Cases điều phối ghép đôi & kết nối (Pure Kotlin)
    │   ├── data/               # NsdManager mDNS adapter & Termux ADB CLI adapter
    │   └── presentation/       # MVI (PairingContract + Composable UI)
    ├── termux/                 # Embedded Termux bootstrap & POSIX shell console
    │   ├── domain/
    │   ├── application/
    │   ├── data/
    │   └── presentation/
    └── tunnel/                 # Reverse WebSocket Tunnel + ForegroundService + Dashboard
        ├── domain/
        ├── application/
        ├── data/
        └── presentation/
```

Xem chi tiết tại [`docs/README.md`](docs/README.md) và [`AGENTS.md`](AGENTS.md).

---

## Khởi chạy & Kiểm thử

```bash
# 1. Kiểm tra ranh giới kiến trúc Clean Arch + Feature-First + MVI & giới hạn <= 350 dòng/file
python3 scripts/check_project.py

# 2. Chạy toàn bộ Unit Tests
./gradlew testDebugUnitTest

# 3. Build APK Debug
./gradlew assembleDebug
```

---

## Giấy phép

Mã nguồn ứng dụng được phát hành theo giấy phép [MIT License](LICENSE). Các gói nhị phân thuộc hệ sinh thái Termux khi đóng gói kèm bootstrap tuân thủ giấy phép gốc của từng thành phần (xem [ADR-0001](docs/adr/0001-embedded-termux-and-wireless-adb.md)).
