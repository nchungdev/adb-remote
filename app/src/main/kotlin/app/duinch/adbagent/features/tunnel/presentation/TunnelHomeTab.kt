package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbExtendedColors
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingSection
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.delay

@Composable
fun TunnelHomeTab(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onPairingIntent: (PairingUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    val clipboard = LocalClipboardManager.current
    val connected = state.connectionState as? TunnelConnectionState.Connected
    val isPaired = state.pairing.pairingState is AdbPairingState.PairedAndConnected
    val isTunnelOnline = state.connectionState is TunnelConnectionState.Connected

    var dismissPairingGuide by rememberSaveable { mutableStateOf(false) }

    val shouldShowPairing = (!isPaired && !isTunnelOnline && !state.isUsbConnected && !dismissPairingGuide) || state.forceShowPairing

    val nowEpochMs by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = connected?.connectedAtEpochMs
    ) {
        if (connected != null) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val visual = resolveStatusVisual(state = state, ext = ext)
    val animatedContainer by animateColorAsState(targetValue = visual.container, label = "orbContainer")
    val animatedTint by animateColorAsState(targetValue = visual.tint, label = "orbTint")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (shouldShowPairing) {
            PairingSection(
                state = state.pairing,
                onIntent = onPairingIntent,
                onSwitchToConnect = {
                    dismissPairingGuide = true
                    onIntent(DashboardUiIntent.SetForceShowPairing(false))
                }
            )
        } else {
            TunnelSwitchboardCard(
                state = state,
                connected = connected,
                animatedContainer = animatedContainer,
                animatedTint = animatedTint,
                icon = visual.icon,
                badgeLabel = visual.badge,
                headline = visual.headline,
                subtitle = visual.subtitle,
                nowEpochMs = nowEpochMs,
                onIntent = onIntent,
                onCopyText = { clipboard.setText(AnnotatedString(it)) }
            )
        }
    }
}

private data class StatusVisual(
    val badge: String,
    val headline: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val container: Color
)

@Composable
private fun resolveStatusVisual(
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
