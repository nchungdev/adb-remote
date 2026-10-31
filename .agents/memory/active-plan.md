# Active Plan: Tool Dashboard UI + Enforce 300-Line Limit

## Objective
1. Redesign Web Management UI into a modern developer tool dashboard (Linear / Docker Desktop / Raycast inspired sidebar layout).
2. Modularize Web UI (`index.html`, `layout.css`, `components.css`, `app.js`, `modals.js`) so every file is <= 300 lines.
3. Refactor all remaining files > 300 LOC in Android & Go server down to <= 300 LOC.
4. Update `scripts/check_project.py` with `MAX_LINES = 300` and include `.go`, `.html`, `.js`, `.css` extensions.
5. Rebuild Go server and restart launch agent.

## Steps
- [x] 1. Modularize Web UI into `server/internal/controller/http/ui/` (`index.html`, `layout.css`, `components.css`, `app.js`, `modals.js`).
- [x] 2. Update `server/internal/controller/http/ui/embed.go` & `router.go` to serve static assets (`/ui/*`).
- [x] 3. Refactor `hub.go` (336 -> 292) and `device_service.go` (335 -> 274).
- [x] 4. Refactor Kotlin files: `DashboardViewModel.kt` (310 -> 249), `TunnelHomeTab.kt` (322 -> 180), `TunnelHistoryTab.kt` (342 -> 235), `WebRtcReverseTunnelAdapter.kt` (325 -> 296).
- [x] 5. Update `scripts/check_project.py` with `MAX_LINES = 300` and multi-extension scan (`.kt`, `.py`, `.go`, `.html`, `.js`, `.css`).
- [x] 6. Run `./gradlew testDebugUnitTest`, `go test ./...`, and `python3 scripts/check_project.py` (All GREEN).
- [x] 7. Build and restart Go server daemon (`com.duinch.adb-manager` LaunchAgent).
