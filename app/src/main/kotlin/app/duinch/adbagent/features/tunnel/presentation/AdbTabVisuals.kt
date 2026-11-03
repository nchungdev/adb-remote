package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState

enum class AdbOverallState {
    CONNECTED,
    CONNECTING,
    RECONNECTING,
    ERROR,
    USB_ACTIVE,
    LOCAL_READY,
    DISCONNECTED
}

@Immutable
data class AdbTabVisual(
    val state: AdbOverallState,
    val icon: ImageVector,
    val selectedColor: Color,
    val selectedTextColor: Color,
    val indicatorColor: Color,
    val unselectedIconColor: Color,
    val contentDescription: String
)

fun resolveAdbOverallState(state: DashboardUiState): AdbOverallState = when {
    state.connectionState is TunnelConnectionState.Connected -> AdbOverallState.CONNECTED
    state.connectionState is TunnelConnectionState.Connecting -> AdbOverallState.CONNECTING
    state.connectionState is TunnelConnectionState.Reconnecting -> AdbOverallState.RECONNECTING
    state.connectionState is TunnelConnectionState.Error -> AdbOverallState.ERROR
    state.pairing.pairingState is AdbPairingState.PairingInProgress -> AdbOverallState.CONNECTING
    state.pairing.pairingState is AdbPairingState.Failed -> AdbOverallState.ERROR
    state.isUsbConnected -> AdbOverallState.USB_ACTIVE
    state.pairing.pairingState is AdbPairingState.PairedAndConnected -> AdbOverallState.LOCAL_READY
    else -> AdbOverallState.DISCONNECTED
}

@Composable
fun rememberAdbTabVisual(
    state: DashboardUiState,
    isDark: Boolean = isSystemInDarkTheme()
): AdbTabVisual {
    val overallState = resolveAdbOverallState(state)
    val unselectedNavColor = MaterialTheme.colorScheme.onSurfaceVariant

    return when (overallState) {
        AdbOverallState.CONNECTED -> AdbTabVisual(
            state = overallState,
            icon = AppIcons.ShieldBolt,
            selectedColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
            selectedTextColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
            indicatorColor = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5),
            unselectedIconColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
            contentDescription = "ADB đang trực tuyến"
        )
        AdbOverallState.CONNECTING, AdbOverallState.RECONNECTING -> AdbTabVisual(
            state = overallState,
            icon = AppIcons.ShieldBolt,
            selectedColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
            selectedTextColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
            indicatorColor = if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7),
            unselectedIconColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
            contentDescription = if (overallState == AdbOverallState.RECONNECTING) {
                "ADB đang tự nối lại"
            } else {
                "Đang kết nối ADB"
            }
        )
        AdbOverallState.ERROR -> AdbTabVisual(
            state = overallState,
            icon = AppIcons.AlertCircle,
            selectedColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
            selectedTextColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
            indicatorColor = if (isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2),
            unselectedIconColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
            contentDescription = "Gián đoạn kết nối ADB"
        )
        AdbOverallState.USB_ACTIVE -> AdbTabVisual(
            state = overallState,
            icon = AppIcons.Usb,
            selectedColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            selectedTextColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            indicatorColor = if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE),
            unselectedIconColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            contentDescription = "ADB đang kết nối qua USB"
        )
        AdbOverallState.LOCAL_READY -> AdbTabVisual(
            state = overallState,
            icon = AppIcons.LinkWireless,
            selectedColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            selectedTextColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            indicatorColor = if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE),
            unselectedIconColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            contentDescription = "Cổng ADB nội bộ đã sẵn sàng"
        )
        AdbOverallState.DISCONNECTED -> AdbTabVisual(
            state = overallState,
            icon = AppIcons.ShieldBolt,
            selectedColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = unselectedNavColor,
            contentDescription = "ADB chưa kết nối"
        )
    }
}

internal fun withStandbySuffix(title: String, connection: TunnelConnectionState): String =
    if ((connection as? TunnelConnectionState.Connected)?.isStandby == true) "$title • Dự phòng" else title
