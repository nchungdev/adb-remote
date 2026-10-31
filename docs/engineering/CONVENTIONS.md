# Naming & Git Conventions (`CONVENTIONS.md`)

## 1. Quy ước Đặt tên Kotlin & MVI

| Thành phần | Quy ước đặt tên | Ví dụ |
|---|---|---|
| Domain Entity / Value Object | Danh từ rõ nghĩa | `WirelessAdbEndpoint`, `TunnelConfig` |
| Domain Gateway / Repository | `*Gateway` hoặc `*Repository` | `AdbPairingGateway`, `TunnelConfigRepository` |
| Application Use Case | `<Động từ><Danh từ>UseCase` | `PairLocalAdbUseCase`, `ToggleReverseTunnelUseCase` |
| Data Adapter | `<Công nghệ><Tên>Adapter` / `*RepositoryImpl` | `AndroidMdnsAdbDiscoveryAdapter`, `WebRtcReverseTunnelAdapter` |
| MVI State | `*UiState` | `DashboardUiState` |
| MVI Intent | `*UiIntent` | `DashboardUiIntent` |
| MVI Side-Effect | `*UiEffect` | `DashboardUiEffect` |
| Compose Screen | `*Screen` / `*Section` / `*Sheet` | `DashboardScreen`, `TermuxConsoleSheet` |

---

## 2. Ngôn ngữ

- **Tên biến, hàm, class, package, commit prefix:** Tiếng Anh chuẩn kỹ thuật.
- **Tài liệu & UI Copy hiển thị cho người dùng:** Tiếng Việt tự nhiên, rõ ràng, ngắn gọn.

---

## 3. Git & Nhánh

- Nhánh chính: `main`.
- Nhánh tính năng/sửa lỗi: `<type>/<slug>` (ví dụ `feat/mdns-auto-pair`, `fix/ws-reconnect-jitter`).
- Commit message theo chuẩn Conventional Commits: `<type>(<scope>): <summary>`.
