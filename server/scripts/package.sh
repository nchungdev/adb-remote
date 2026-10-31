#!/bin/bash
set -e

VERSION="${1:-v0.5.17}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
TMP_DIR="$ROOT_DIR/build/pkg-tmp"

echo "=========================================================="
echo " Đóng gói ADB Device Manager ($VERSION)"
echo " Nền tảng: macOS, Windows, Linux (amd64 + arm64)"
echo "=========================================================="

rm -rf "$DIST_DIR" "$TMP_DIR"
mkdir -p "$DIST_DIR" "$TMP_DIR"

build_target() {
    local os="$1"
    local arch="$2"
    local out_name="$3"
    echo "🔨 Đang biên dịch: $os/$arch..."
    CGO_ENABLED=0 GOOS="$os" GOARCH="$arch" go build \
        -ldflags="-s -w" \
        -o "$out_name" \
        "$ROOT_DIR/cmd/api"
}

# 1. macOS (Apple Silicon + Intel)
for arch in arm64 amd64; do
    pkg_name="adb-device-manager-${VERSION}-macos-${arch}"
    pkg_dir="$TMP_DIR/$pkg_name"
    mkdir -p "$pkg_dir"
    build_target "darwin" "$arch" "$pkg_dir/adb-device-manager"
    chmod +x "$pkg_dir/adb-device-manager"
    cp "$ROOT_DIR/packaging/macos/install.sh" "$pkg_dir/install.sh"
    cp "$ROOT_DIR/packaging/macos/uninstall.sh" "$pkg_dir/uninstall.sh"
    cp "$ROOT_DIR/packaging/README.txt" "$pkg_dir/README.txt"
    chmod +x "$pkg_dir/install.sh" "$pkg_dir/uninstall.sh"

    tar -czf "$DIST_DIR/${pkg_name}.tar.gz" -C "$TMP_DIR" "$pkg_name"
    echo "📦 Tạo gói thành công: $DIST_DIR/${pkg_name}.tar.gz"
done

# 2. Linux (x86_64 + ARM64)
for arch in amd64 arm64; do
    pkg_name="adb-device-manager-${VERSION}-linux-${arch}"
    pkg_dir="$TMP_DIR/$pkg_name"
    mkdir -p "$pkg_dir"
    build_target "linux" "$arch" "$pkg_dir/adb-device-manager"
    chmod +x "$pkg_dir/adb-device-manager"
    cp "$ROOT_DIR/packaging/linux/install.sh" "$pkg_dir/install.sh"
    cp "$ROOT_DIR/packaging/linux/uninstall.sh" "$pkg_dir/uninstall.sh"
    cp "$ROOT_DIR/packaging/README.txt" "$pkg_dir/README.txt"
    chmod +x "$pkg_dir/install.sh" "$pkg_dir/uninstall.sh"

    tar -czf "$DIST_DIR/${pkg_name}.tar.gz" -C "$TMP_DIR" "$pkg_name"
    echo "📦 Tạo gói thành công: $DIST_DIR/${pkg_name}.tar.gz"
done

# 3. Windows (x64 + ARM64)
for arch in amd64 arm64; do
    pkg_name="adb-device-manager-${VERSION}-windows-${arch}"
    pkg_dir="$TMP_DIR/$pkg_name"
    mkdir -p "$pkg_dir"
    build_target "windows" "$arch" "$pkg_dir/adb-device-manager.exe"
    cp "$ROOT_DIR/packaging/windows/install.bat" "$pkg_dir/install.bat"
    cp "$ROOT_DIR/packaging/windows/uninstall.bat" "$pkg_dir/uninstall.bat"
    cp "$ROOT_DIR/packaging/windows/install.ps1" "$pkg_dir/install.ps1"
    cp "$ROOT_DIR/packaging/windows/uninstall.ps1" "$pkg_dir/uninstall.ps1"
    cp "$ROOT_DIR/packaging/README.txt" "$pkg_dir/README.txt"

    (cd "$TMP_DIR" && zip -q -r "$DIST_DIR/${pkg_name}.zip" "$pkg_name")
    echo "📦 Tạo gói thành công: $DIST_DIR/${pkg_name}.zip"
done

# Tạo Checksums SHA-256
cd "$DIST_DIR"
shasum -a 256 * > checksums.txt
echo "=========================================================="
echo " ĐÃ HOÀN TẤT ĐÓNG GÓI! Danh sách file trong dist/:"
ls -lh "$DIST_DIR"
echo "=========================================================="
cat "$DIST_DIR/checksums.txt"
