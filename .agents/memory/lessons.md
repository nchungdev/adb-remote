# Lessons Learned & Pitfalls

## Navigation Bar Dynamic State Mapping
- When mapping dynamic domain/tunnel states to navigation bar items, separate the resolver logic into an immutable presentation model (`AdbTabVisuals.kt`) rather than inlining complex `when` branches inside the Screen composable.
- Always animate color state transitions via `animateColorAsState` and icons via `Crossfade` for fluid state updates in Jetpack Compose.
- Tint `unselectedIconColor` with the active status color so users can observe real-time ADB connection status even when browsing other tabs (History/Settings).

## Purging Legacy Transports & Upgrading to Pure P2P
- When migrating transports (e.g. WebSocket reverse tunnel -> WebRtcReverseTunnelAdapter), bind the new adapter directly to the domain port `ReverseTunnelGateway` in `AppContainer`.
- Remove intermediate multiplexing/dynamic gateways if only one pure transport remains.
- Eliminate deprecated configuration properties (`tunnelMode`, mode constants) and adapt URI validation to support multiple schemes (http/https/ws/wss) for local and remote signaling.
- Always check Android API levels on platform APIs (`caps.transportInfo` requires API 29 / Android Q) to avoid `NewApi` lint failures.

## Wireless Pairing UX & Transparent Dialogs
- Do not rely solely on Android notification RemoteInput actions: system UI often collapses them by default, hiding the input field from the user.
- Always configure `Notification.BigTextStyle` with `PRIORITY_MAX` and set `contentIntent` targeting a transparent floating dialog activity (`PairingPromptActivity`).
- Use `Theme.AdbAgent.TransparentDialog` with semi-transparent dimmed Compose backdrop and `FocusRequester` on the 6-digit text field so the numeric keyboard pops up immediately upon discovery.
- Avoid technical jargon in UI copy: use "Ghép nối nhanh" instead of "Ghép đôi qua thông báo", and "Bật lại ADB" instead of "Kích hoạt lại cổng 5555".

## Hook Verification & 300 LOC Guardrails
- Pre-commit hooks (`.githooks/pre-commit`) delegate LOC checks to `scripts/check_project.py`.
- If `check_project.py` sets `MAX_LINES = 350` or only matches `("*.kt", "*.py")`, files in Go/Web UI or files between 301 and 350 lines will silently bypass the hook.
- `project_pro audit --fail-on-error` only fails on God-files > 450 LOC (issuing WARN for 301-450 LOC). To enforce strict <= 300 LOC, `check_project.py` must enforce `MAX_LINES = 300` across all source types (`.kt`, `.py`, `.go`, `.html`, `.js`, `.css`).


## 2026-11-03: Suspended Snackbar Blocks Navigation
- `showSnackbar` suspends until dismissal. `DashboardScreen` returns early for fullscreen pairing, so its SnackbarHost is absent and an existing message can wait indefinitely. Serial effect collection then blocks Settings/QR navigation even though the Settings intent works.
- Process cancellable dashboard effects with `collectLatest`; regression-test navigation with a message callback that calls `awaitCancellation()`. Check the actual resumed Settings activity on debug and R8 release, not merely whether Settings appears anywhere in dumpsys.

## 2026-11-03: Same-Alias Connection Slots Ping-Pong
- The server allows one session per device alias, and every slot on a phone sends the same alias. An evicted client that treats the close as a network loss reconnects and evicts the other slot, forever. `retryAttempt` resets on open, so backoff never grows and the loop runs at ~1s.
- Do not evict: keep every path alive in the device slot and route ADB through the best one (LAN > remote, then newest). Only touch adb when the active path changes. Guard transport callbacks with a session generation: closing a session fires callbacks that otherwise tear down its successor.
- `WSConn.ReadMessage` swallows pings in its own loop, so a deadline set by the caller never extends while idle. Set the deadline per frame.

## 2026-11-03: Quick Tunnel URL Is Printed Before It Resolves
- cloudflared logs the trycloudflare URL ~1.5s before the DNS record exists. Any client resolving it then gets NXDOMAIN, and the OS caches that for 60s+ while 1.1.1.1 recovers instantly, so it looks like a slow or broken first start.
- Publish only after "Registered tunnel connection" and a successful lookup against a public resolver. Polling through the system resolver would poison the very cache being protected.

## 2026-11-03: Pairing UI Must Be Backed By The Server
- A client-side approval dialog protects nothing: `/api/tunnel` and `/api/webrtc/signal` were open to anyone holding the public tunnel URL. Enforce on the server and test with a non-loopback `RemoteAddr`, since host-local requests bypass the gate. An emulator reaching the host through `10.0.2.2` also looks local, so test against the LAN IP.
- Use a client-generated secret and store only its hash: the approval then survives URL changes, and no endpoint ever hands out a credential that polling could steal.
