#!/bin/bash
set -e

APP_NAME="adb-device-manager"
INSTALL_DIR="$HOME/.local/share/adb-device-manager"
BIN_DIR="$HOME/.local/bin"
SYSTEMD_USER_DIR="$HOME/.config/systemd/user"

echo "================================================="
echo " Cài đặt ADB Device Manager cho Linux (Systemd)"
echo "================================================="

mkdir -p "$INSTALL_DIR"
mkdir -p "$BIN_DIR"
mkdir -p "$SYSTEMD_USER_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$SCRIPT_DIR/$APP_NAME" "$INSTALL_DIR/$APP_NAME"
chmod +x "$INSTALL_DIR/$APP_NAME"
ln -sf "$INSTALL_DIR/$APP_NAME" "$BIN_DIR/$APP_NAME"

cat <<EOF > "$SYSTEMD_USER_DIR/${APP_NAME}.service"
[Unit]
Description=ADB Web Device Manager & P2P Tunnel Hub
After=network.target

[Service]
ExecStart=${INSTALL_DIR}/${APP_NAME}
Restart=always
RestartSec=5s
Environment=PATH=/usr/local/bin:/usr/bin:/bin:${BIN_DIR}

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now "${APP_NAME}.service"

sleep 1

if curl -s http://127.0.0.1:8080/healthz 2>/dev/null | grep -q "ok"; then
    echo "✅ Cài đặt thành công! Dịch vụ đang chạy tại: http://127.0.0.1:8080"
    xdg-open "http://127.0.0.1:8080" 2>/dev/null || true
else
    echo "⚠️  Dịch vụ đã đăng ký. Kiểm tra trạng thái: systemctl --user status ${APP_NAME}"
fi
