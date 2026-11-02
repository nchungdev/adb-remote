#!/usr/bin/env bash
set -euo pipefail

# Package ADB Device Manager Server into a native macOS Menu Bar .app
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
APP_NAME="ADB Device Manager"
BUNDLE_DIR="$DIST_DIR/$APP_NAME.app"

echo "=== 1. Building Standalone Server Binary (Go Native) ==="
cd "$ROOT_DIR/server"
go build -ldflags="-s -w" -o "$DIST_DIR/adb-device-manager" ./cmd/api

echo "=== 2. Creating macOS App Bundle ==="
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/Contents/MacOS"
mkdir -p "$BUNDLE_DIR/Contents/Resources"

# Copy binary
cp "$DIST_DIR/adb-device-manager" "$BUNDLE_DIR/Contents/MacOS/adb-device-manager"
chmod +x "$BUNDLE_DIR/Contents/MacOS/adb-device-manager"

# Create Info.plist with LSUIElement = true (Menu Bar Only, no Dock icon)
cat << 'EOF' > "$BUNDLE_DIR/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>adb-device-manager</string>
    <key>CFBundleIdentifier</key>
    <string>app.duinch.adbagent.server</string>
    <key>CFBundleName</key>
    <string>ADB Device Manager</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>12.0</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

echo "=== 3. Installing to ~/Applications (Optional) ==="
mkdir -p "$HOME/Applications"
cp -R "$BUNDLE_DIR" "$HOME/Applications/"

echo ""
echo "=== BUILD SUCCESSFUL ==="
echo "App created at: $BUNDLE_DIR"
echo "Installed to:   $HOME/Applications/$APP_NAME.app"
echo "To run now:     open '$HOME/Applications/$APP_NAME.app'"
