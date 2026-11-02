package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.duinch.adbagent.core.designsystem.AdbExtendedColors
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState

internal data class StatusVisual(
    val badge: String,
    val headline: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val container: Color
)

@Composable
internal fun resolveStatusVisual(
    state: DashboardUiState,
    ext: AdbExtendedColors
): StatusVisual = when (val conn = state.connectionState) {
    is TunnelConnectionState.Connected -> StatusVisual(
        badge = "ĐANG TRỰC TUYẾN",
        headline = if (state.isUsbConnected) "Đường hầm Mac mini & USB" else "Đường hầm Mac mini",
        subtitle = conn.mappedServerPort?.let { "127.0.0.1:$it" } ?: "Đã kết nối thông suốt P2P",
        icon = AppIcons.ShieldBolt,
        tint = ext.statusConnected,
        container = ext.statusConnectedContainer
    )
    is TunnelConnectionState.Connecting -> StatusVisual(
        badge = "ĐANG KẾT NỐI...",
        headline = "Đang kết nối Mac mini",
        subtitle = "Đang thiết lập kênh TLS bảo mật",
        icon = AppIcons.Refresh,
        tint = ext.statusWarning,
        container = ext.statusWarningContainer
    )
    is TunnelConnectionState.Reconnecting -> StatusVisual(
        badge = "ĐANG NỐI LẠI (${conn.attempt})",
        headline = "Đang tự động nối lại",
        subtitle = "Thử lại sau ${conn.nextRetryDelayMs / 1000}s",
        icon = AppIcons.Refresh,
        tint = ext.statusWarning,
        container = ext.statusWarningContainer
    )
    is TunnelConnectionState.Error -> StatusVisual(
        badge = "GIÁN ĐOẠN KẾT NỐI",
        headline = "Gián đoạn đường hầm",
        subtitle = conn.message,
        icon = AppIcons.AlertCircle,
        tint = MaterialTheme.colorScheme.error,
        container = MaterialTheme.colorScheme.errorContainer
    )
    TunnelConnectionState.Disconnected -> when {
        state.isUsbConnected -> StatusVisual(
            badge = "ĐÃ KẾT NỐI",
            headline = "Cáp USB trực tiếp",
            subtitle = "Máy tính nhận diện trực tiếp qua USB",
            icon = AppIcons.Usb,
            tint = ext.statusConnected,
            container = ext.statusConnectedContainer
        )
        state.isAtHomeWifi || state.pairing.pairingState is AdbPairingState.PairedAndConnected -> StatusVisual(
            badge = "SẴN SÀNG KẾT NỐI",
            headline = if (state.isAtHomeWifi) "Wi-Fi nội bộ (${state.trustedHomeSsidInput.ifBlank { "Nhà" }})" else "Wi-Fi nội bộ",
            subtitle = if (state.localWifiIp.isNotBlank()) "Cổng ADB sẵn sàng nhận lệnh" else "Cổng ADB nội bộ đã sẵn sàng",
            icon = AppIcons.LinkWireless,
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer
        )
        else -> StatusVisual(
            badge = "CHƯA KẾT NỐI",
            headline = "Sẵn sàng kết nối",
            subtitle = "Mở đường hầm bảo mật hoặc cắm cáp USB",
            icon = AppIcons.ShieldBolt,
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer
        )
    }
}
