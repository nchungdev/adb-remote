# Active Plan: macOS Status Bar App + 6-Digit Room Code P2P Signaling

## Objective
1. Package Go server daemon into a native macOS Menu Bar (Status Bar) App (`ADB Device Manager.app`) using `github.com/getlantern/systray`.
2. Implement 6-Digit P2P Room Code (`XXX-XXX`) with zero-config public MQTT signaling relay so devices can connect across different networks (4G/5G/Wi-Fi) without opening ports or creating Cloudflare Workers.
3. Add Menu Bar actions: Status indicator, copy P2P Room Code, copy LAN IP, Open Web Dashboard, QR pairing, Pause/Resume, Restart ADB, and Quit.
4. Update packaging script (`scripts/package_macos.sh`) to build `.app` bundle with `LSUIElement=true`.
5. Enforce 100% strict `<= 300` LOC limit per file and verify via `check_project.py` and test suites.

## Steps
- [ ] 1. Create `server/internal/signaling/mqtt_relay.go` to handle P2P 6-digit room code signaling.
- [ ] 2. Create `server/internal/tray/tray.go` with status bar UI and clipboard actions.
- [ ] 3. Update `server/cmd/api/main.go` to integrate systray when running on macOS.
- [ ] 4. Create `scripts/package_macos.sh` to build macOS `.app` bundle.
- [ ] 5. Run tests (`go test ./...`, `./gradlew testDebugUnitTest`, `python3 scripts/check_project.py`).
- [ ] 6. Build and launch status bar app, verify menu items.
