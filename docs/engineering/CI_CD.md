# CI/CD & Versioning (`CI_CD.md`)

## 1. Quản lý Phiên bản

Phiên bản ứng dụng được định nghĩa duy nhất tại tệp [`version.properties`](../../version.properties):

```properties
versionMajor=0
versionMinor=1
versionPatch=0
versionClassifier=alpha.1
versionCode=1
```

`app/build.gradle.kts` tự động đọc tệp này để sinh `versionName` (`0.1.0-alpha.1`) và `versionCode` (`1`).

---

## 2. Pipeline Kiểm tra Chuẩn

Mọi Pull Request vào nhánh `main` chạy tuần tự 3 bước:

1. **Architecture & Hygiene Check:** `python3 scripts/check_project.py`
2. **JVM Unit Tests:** `./gradlew testDebugUnitTest`
3. **Debug APK Packaging:** `./gradlew assembleDebug`

Không bao giờ tải khóa ký cá nhân (`release.keystore`) hay token bí mật dưới dạng văn bản rõ lên kho lưu trữ; sử dụng GitHub Actions Secrets khi xuất bản bản Release.
