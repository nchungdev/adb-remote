# Project Structure (`PROJECT_STRUCTURE.md`)

```text
adb-agent/
├── AGENTS.md                        # Quy tắc bắt buộc cho AI Agents
├── CLAUDE.md                        # Liên kết hướng dẫn cho Claude
├── CONTRIBUTING.md                  # Hướng dẫn đóng góp & PR
├── LICENSE                          # Giấy phép MIT
├── README.md                        # Tổng quan dự án & lệnh chạy nhanh
├── scripts/
│   └── check_project.py             # Cổng kiểm tra kiến trúc & giới hạn <= 350 dòng
├── docs/
│   ├── README.md                    # Mục lục tài liệu
│   ├── product/                     # Đặc tả sản phẩm, UX, Roadmap
│   ├── engineering/                 # Kiến trúc, quy tắc code, quy ước, kiểm thử, CI/CD
│   └── adr/                         # Hồ sơ quyết định kiến trúc (ADR)
├── server/                          # Máy chủ Go Server & Cross-Platform Packaging
│   ├── cmd/api/main.go              # Điểm khởi chạy daemon server
│   ├── internal/                    # Core domain, WebRTC Hub, ADB controller, Web UI
│   ├── packaging/                   # LaunchAgent (macOS), Systemd (Linux), VBS (Windows)
│   ├── scripts/package.sh           # Script đóng gói phân phối đa nền tảng
│   ├── dist/                        # Gói cài đặt phân phối (.tar.gz, .zip)
│   └── go.mod / go.sum
└── app/                             # Ứng dụng Android Client độc lập
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── version.properties           # Quản lý phiên bản SemVer tập trung
    ├── gradlew / gradlew.bat
    ├── gradle/
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/values/strings.xml
        │   └── kotlin/app/duinch/adbagent/
        │       ├── AdbAgentApp.kt
        │       ├── MainActivity.kt
        │       ├── core/
        │       │   ├── designsystem/    # Material 3 Theme & bảng màu kỹ thuật
        │       │   ├── di/              # Constructor DI Container
        │       │   └── model/           # Kiểu dữ liệu dùng chung thuần Kotlin
        │       └── features/
        │           ├── pairing/
        │           │   ├── domain/
        │           │   ├── application/
        │           │   ├── data/
        │           │   └── presentation/
        │           ├── termux/
        │           │   ├── domain/
        │           │   ├── application/
        │           │   ├── data/
        │           │   └── presentation/
        │           └── tunnel/
        │               ├── domain/
        │               ├── application/
        │               ├── data/
        │               └── presentation/
        └── test/
            └── kotlin/app/duinch/adbagent/features/
                ├── pairing/application/
                ├── termux/application/
                └── tunnel/application/
```

## Nơi đặt tệp mới

- Thêm thực thể hoặc hợp đồng cổng giao tiếp của một tính năng: `features/<feature>/domain/`.
- Thêm luồng nghiệp vụ / kiểm tra điều kiện: `features/<feature>/application/`.
- Thêm tích hợp Android SDK / Socket / File / Service: `features/<feature>/data/`.
- Thêm màn hình hoặc hợp đồng MVI: `features/<feature>/presentation/`.
