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
