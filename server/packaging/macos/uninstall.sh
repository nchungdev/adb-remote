#!/bin/bash
SERVICE_NAME="com.duinch.adb-manager"
INSTALL_DIR="$HOME/.local/share/adb-device-manager"
BIN_DIR="$HOME/.local/bin"
PLIST_PATH="$HOME/Library/LaunchAgents/${SERVICE_NAME}.plist"

echo "Đang gỡ bỏ ADB Device Manager..."
launchctl bootout "gui/$(id -u)/${SERVICE_NAME}" 2>/dev/null || launchctl unload "$PLIST_PATH" 2>/dev/null || true
rm -f "$PLIST_PATH"
rm -f "$BIN_DIR/adb-device-manager"
rm -f "$INSTALL_DIR/adb-device-manager"
echo "✅ Đã gỡ bỏ dịch vụ thành công."
