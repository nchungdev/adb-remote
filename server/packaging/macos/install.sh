#!/bin/bash
set -e

APP_NAME="adb-device-manager"
SERVICE_NAME="com.duinch.adb-manager"
INSTALL_DIR="$HOME/.local/share/adb-device-manager"
BIN_DIR="$HOME/.local/bin"
PLIST_PATH="$HOME/Library/LaunchAgents/${SERVICE_NAME}.plist"

echo "================================================="
echo " Cài đặt ADB Device Manager cho macOS"
echo "================================================="

mkdir -p "$INSTALL_DIR"
mkdir -p "$BIN_DIR"
mkdir -p "$HOME/Library/LaunchAgents"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$SCRIPT_DIR/$APP_NAME" "$INSTALL_DIR/$APP_NAME"
chmod +x "$INSTALL_DIR/$APP_NAME"
ln -sf "$INSTALL_DIR/$APP_NAME" "$BIN_DIR/$APP_NAME"

cat <<EOF > "$PLIST_PATH"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
	<dict>
		<key>Label</key>
		<string>${SERVICE_NAME}</string>
		<key>ProgramArguments</key>
		<array>
			<string>${INSTALL_DIR}/${APP_NAME}</string>
		</array>
		<key>RunAtLoad</key>
		<true/>
		<key>KeepAlive</key>
		<true/>
		<key>StandardOutPath</key>
		<string>${HOME}/Library/Logs/adb-manager.out.log</string>
		<key>StandardErrorPath</key>
		<string>${HOME}/Library/Logs/adb-manager.err.log</string>
		<key>ThrottleInterval</key>
		<integer>5</integer>
	</dict>
</plist>
EOF

launchctl bootout "gui/$(id -u)/${SERVICE_NAME}" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST_PATH" 2>/dev/null || launchctl load -w "$PLIST_PATH"

sleep 1

if curl -s http://127.0.0.1:8080/healthz | grep -q "ok"; then
    echo "✅ Cài đặt thành công! Dịch vụ đang chạy tại: http://127.0.0.1:8080"
    open "http://127.0.0.1:8080" 2>/dev/null || true
else
    echo "⚠️  Dịch vụ đã đăng ký. Đang khởi động trên http://127.0.0.1:8080"
fi
