# Active Plan: Purge 100% Cloudflare & WebSocketReverseTunnelAdapter -> Pure WebRTC P2P

## Objective
Remove all remaining Cloudflare-related components and legacy WebSocketReverseTunnelAdapter from the Android application, making WebRtcReverseTunnelAdapter the sole tunnel implementation for P2P connection.

## Scope of Changes
1. **Remove `WebSocketReverseTunnelAdapter.kt`**:
   - Delete `app/src/main/kotlin/app/duinch/adbagent/features/tunnel/data/WebSocketReverseTunnelAdapter.kt`.
2. **Simplify `DynamicReverseTunnelGateway.kt` / `AppContainer.kt`**:
   - `WebRtcReverseTunnelAdapter` implements `ReverseTunnelGateway`.
   - Update `DynamicReverseTunnelGateway.kt` or remove it, wiring `WebRtcReverseTunnelAdapter` directly to `AppContainer.kt`.
3. **Clean Domain / Config Models (`TunnelModels.kt`, `SharedPrefsTunnelConfigRepository.kt`)**:
   - Remove `MODE_WEBSOCKET` and any legacy Cloudflare / WebSocket specific properties or validations.
   - Support `http://`, `https://`, `ws://`, `wss://` for signaling server URL.
4. **Clean Presentation & UI**:
   - Check `TunnelSettingsDetailSheets.kt`, `TunnelSettingsTab.kt`, `TunnelHomeTab.kt`, `TunnelHomeComponents.kt`, `DashboardScreen.kt`, `DashboardContract.kt`, `DashboardViewModel.kt` for any remnants of Cloudflare or WebSocket mode switches.
   - Remove any residual dialogs or unused imports.
5. **Update Documentation & Tests**:
   - Run tests: `./app/gradlew testDebugUnitTest`.
   - Update `docs/` and `README.md` if any references to Cloudflare tunnel remain.
