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
