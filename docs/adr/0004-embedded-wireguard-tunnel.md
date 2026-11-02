# ADR 0004: Đường hầm Nhúng WireGuard (Zero 3rd-Party App)

- **Trạng thái:** Proposed / Reference Architecture
- **Ngày:** 2026-10-31
- **Tham chiếu:** [ADR 0003](0003-p2p-webrtc-datachannel-tunnel.md)

---

## 1. Bối cảnh

Hệ thống hiện tại sử dụng **WebRTC DataChannel (L4/Application Stream)** làm kênh P2P chính kết nối giữa ứng dụng Android và máy chủ Go. Mặc dù WebRTC DataChannel hoạt động mà không cần quyền đặc biệt, trong một số kịch bản doanh nghiệp hoặc môi trường mạng đóng:
- Cần một đường hầm IP ảo hoàn chỉnh (L3 Virtual TUN) để hỗ trợ toàn bộ các giao thức mạng thay vì chỉ stream nhị phân riêng lẻ.
- Tận dụng hiệu năng tăng tốc phần cứng của mã hóa ChaCha20-Poly1305 với độ trễ thấp tối đa.
- Loại bỏ hoàn toàn sự phụ thuộc vào các ứng dụng VPN bên thứ 3 (như WireGuard Client, Tailscale, Cloudflare WARP).

---

## 2. Quyết định Kiến trúc

Triển khai mô hình **Embedded WireGuard** tự code 100%:
1. **Android Client:**
   - Sử dụng API chuẩn `android.net.VpnService` để tạo interface mạng ảo TUN (`10.0.0.2/24`).
   - Nhúng engine Go/C native thông qua thư viện chính thức `com.wireguard.android:tunnel`.
   - Không yêu cầu người dùng cài đặt ứng dụng WireGuard từ Google Play.
2. **Host / Server Daemon (Go):**
   - Sử dụng `golang.zx2c4.com/wireguard` và `netstack` để tạo userspace TUN hoặc quản lý TUN interface trên máy chủ (`10.0.0.1/24`).
   - Lắng nghe cổng UDP WireGuard (mặc định `51820`).
   - Định tuyến lưu lượng ADB (cổng 5555) qua IP nội bộ mạng ảo `10.0.0.2`.

---

## 3. Bản thiết kế triển khai (Implementation Blueprint)

### 3.1. Phía Android (Kotlin)

```kotlin
// Khởi tạo và kích hoạt WireGuard qua VpnService
class EmbeddedWireGuardTunnel(private val context: Context) {
    private val backend = GoBackend(context.applicationContext)

    fun connect(
        serverPublicKey: String,
        serverEndpoint: String,
        clientPrivateKey: String,
        virtualClientIp: String = "10.0.0.2/32"
    ) {
        val config = Config.Builder()
            .setInterface(
                Interface.Builder()
                    .addAddress(InetNetwork.parse(virtualClientIp))
                    .parsePrivateKey(clientPrivateKey)
                    .build()
            )
            .addPeer(
                Peer.Builder()
                    .parsePublicKey(serverPublicKey)
                    .parseEndpoint(serverEndpoint)
                    .addAllowedIp(InetNetwork.parse("10.0.0.1/32"))
                    .build()
            )
            .build()

        backend.setState(AdbTunnelHandle, Tunnel.State.UP, config)
    }

    fun disconnect() {
        backend.setState(AdbTunnelHandle, Tunnel.State.DOWN, null)
    }
}
```

### 3.2. Phía Server (Go - Userspace Netstack)

```go
// Khởi tạo WireGuard Device trong Go mà không cần quyền root kernel
func StartUserspaceWireGuard(serverPrivKey string, clientPubKey string, port int) (*device.Device, error) {
    localIP := netip.MustParseAddr("10.0.0.1")
    tunDev, _, err := netstack.CreateNetTUN([]netip.Addr{localIP}, []netip.Addr{}, 1420)
    if err != nil {
        return nil, err
    }

    dev := device.NewDevice(tunDev, conn.NewDefaultBind(), device.NewLogger(device.LogLevelInfo, "[WG-ADB] "))
    ipcConfig := fmt.Sprintf("private_key=%s\nlisten_port=%d\npublic_key=%s\nallowed_ip=10.0.0.2/32\n",
        serverPrivKey, port, clientPubKey)

    if err := dev.IpcSet(ipcConfig); err != nil {
        dev.Close()
        return nil, err
    }
    return dev, nil
}
```

---

## 4. Bảng phân tích so sánh kỹ thuật

| Tiêu chí | WebRTC DataChannel (ADR 0003) | Embedded WireGuard (ADR 0004) |
| :--- | :--- | :--- |
| **Tầng mạng (OSI)** | Layer 4 (SCTP / DTLS stream) | Layer 3 (IP Packet / Virtual TUN) |
| **Quyền truy cập trên Android** | **Zero permission** (Không cần quyền) | Cần chấp nhận quyền `VpnService` 1 lần |
| **Ảnh hưởng các VPN khác** | **Không ảnh hưởng** (Chạy song song) | Chiếm slot VPN duy nhất của hệ thống |
| **Khả năng xuyên NAT / Firewall** | **Xuất sắc** (Tự động nhờ ICE / STUN) | Cần Server mở cổng UDP hoặc có Public IP |
| **Độ trễ truyền gói tin** | Cực thấp (DTLS 1.3) | Thấp nhất (Kernel/Userspace ChaCha20) |

---

## 5. Hệ quả & Ứng dụng

- **Hiện tại**: Dự án tiếp tục duy trì **WebRTC DataChannel** làm kênh mặc định vì không làm gián đoạn các kết nối VPN cá nhân/công ty của lập trình viên và xuyên NAT tự động mà không cần cấu hình port UDP trên router.
- **Dự phòng**: ADR 0004 đóng vai trò tài liệu chuẩn (Standard Reference Blueprint) sẵn sàng kích hoạt khi cần giải pháp L3 IP VPN cho môi trường mạng biệt lập.
