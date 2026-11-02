# Product Roadmap (`ROADMAP.md`)

| Phase | Mục tiêu | Hạng mục chính |
|---|---|---|
| **P0 — Foundation** | Khởi tạo kiến trúc & Repo | Clean Architecture + Feature-First + MVI, Docs, `check_project.py`, Gradle CI-ready build, Unit Tests. |
| **P1 — Embedded Termux & ADB** | Đóng gói Bootstrap thực tế | Tích hợp gói `bootstrap-aarch64` (`adb`, `openssl`), quản lý khóa RSA bền vững trong `filesDir/home/.android`. |
| **P2 — Smart Pairing UX** | Ghép đôi từ thanh Thông báo | Notification Direct-Reply cho phép nhập mã 6 số ngay trên thanh thông báo khi mở màn hình *Wireless Debugging* (giống Shizuku/LADB). |
| **P3 — Full Multiplexed P2P Bridge** | Cầu nối nhị phân P2P WebRTC | Ghép kênh TCP-over-WebRTC DataChannel trực tiếp (Zero Port Forwarding) qua STUN/ICE. |
| **P4 — Boot & Battery Resilience** | Hoạt động bền bỉ 24/7 | `BOOT_COMPLETED` receiver, hướng dẫn miễn trừ tối ưu pin, thống kê băng thông & độ trễ ping. |
