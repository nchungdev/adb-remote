# Contributing Guide

Cảm ơn bạn đã đóng góp cho **ADB Agent**!

## Quy trình phát triển

1. Đọc kỹ [`AGENTS.md`](AGENTS.md) và [`docs/engineering/ARCHITECTURE.md`](docs/engineering/ARCHITECTURE.md).
2. Tổ chức mã nguồn theo đúng cấu trúc **Feature-First + Clean Architecture + MVI**:
   - `features/<feature>/domain` — Pure Kotlin models & interfaces.
   - `features/<feature>/application` — Pure Kotlin Use Cases.
   - `features/<feature>/data` — Android/Network/Termux runtime adapters.
   - `features/<feature>/presentation` — MVI (`*UiState`, `*UiIntent`, `*UiEffect`) + Stateless Compose UI.
3. Đảm bảo mỗi tệp nguồn `<= 350` dòng và tuân thủ `.editorconfig`.
4. Bổ sung Unit Test cho `domain` và `application` use cases.
5. Chạy cổng kiểm tra trước khi mở PR:
   ```bash
   python3 scripts/check_project.py
   ./gradlew testDebugUnitTest assembleDebug
   ```

## Quy ước Commit

Sử dụng Conventional Commits:
- `feat(<feature>): ...`
- `fix(<feature>): ...`
- `refactor(<feature>): ...`
- `test(<feature>): ...`
- `docs: ...`
- `chore: ...`
