# ADR 0001: Nhúng Môi trường Termux Runtime & Tự động Dò mDNS Wireless ADB

- **Trạng thái:** Approved
- **Ngày:** 2026-10-31

## Bối cảnh

Người dùng muốn có trải nghiệm 1-click trên điện thoại Android để kết nối ADB mà không phải cài đặt rời ứng dụng Termux rồi tự gõ các câu lệnh `pkg install`, `adb pair`, `adb connect`.

## Quyết định

1. **Cô lập Tầng Runtime qua `TermuxRuntimeGateway`:**
   - Môi trường POSIX thu gọn được giải nén vào không gian riêng của ứng dụng:
     - `PREFIX = <filesDir>/usr`
     - `HOME = <filesDir>/home`
     - `TMPDIR = <filesDir>/usr/tmp`
   - Tiến trình con chạy với `LD_LIBRARY_PATH=$PREFIX/lib` và `PATH=$PREFIX/bin:/system/bin`, lưu trữ cặp khóa RSA ADB cố định tại `$HOME/.android/adbkey` để chỉ cần ghép đôi một lần duy nhất.
2. **Tự động Dò cổng bằng Android `NsdManager`:**
   - Lắng nghe hai dịch vụ mDNS chuẩn của Android 11+:
     - `_adb-tls-pairing._tcp` (cổng ghép đôi tạm thời khi mở popup mã 6 số)
     - `_adb-tls-connect._tcp` (cổng kết nối ADB TLS hoạt động hiện hành)
3. **Ranh giới Bản quyền (Licensing):**
   - Mã nguồn của ứng dụng `adb-agent` giữ giấy phép MIT.
   - Khi đóng gói kèm các tệp nhị phân từ kho Termux (`termux-packages` / `android-tools` theo Apache-2.0 hoặc GPLv3 tuỳ gói), tuân thủ phân phối thông báo bản quyền tương ứng của từng thành phần nhị phân.

## Hệ quả

- Loại bỏ hoàn toàn thao tác gõ dòng lệnh thủ công cho người dùng cuối nhưng vẫn giữ được sức mạnh linh hoạt của shell Termux trong ngăn kéo Console.
- Dễ dàng hoán đổi sang thư viện ADB TLS thuần Kotlin/JNI trong tương lai mà không ảnh hưởng tới tầng `domain`, `application` và `presentation`.
