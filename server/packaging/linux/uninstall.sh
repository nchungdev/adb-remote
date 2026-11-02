#!/bin/bash
APP_NAME="adb-device-manager"
INSTALL_DIR="$HOME/.local/share/adb-device-manager"
BIN_DIR="$HOME/.local/bin"
SERVICE_PATH="$HOME/.config/systemd/user/${APP_NAME}.service"

echo "Đang gỡ bỏ ADB Device Manager..."
systemctl --user stop "${APP_NAME}.service" 2>/dev/null || true
systemctl --user disable "${APP_NAME}.service" 2>/dev/null || true
rm -f "$SERVICE_PATH"
systemctl --user daemon-reload 2>/dev/null || true
rm -f "$BIN_DIR/${APP_NAME}"
rm -f "$INSTALL_DIR/${APP_NAME}"
echo "✅ Đã gỡ bỏ dịch vụ thành công."
