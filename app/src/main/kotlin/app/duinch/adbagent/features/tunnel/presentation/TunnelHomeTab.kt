package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingSection
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.updater.presentation.CompactUpdateAlertBanner
import app.duinch.adbagent.features.updater.presentation.UpdaterUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdaterUiState
import kotlinx.coroutines.delay

@Composable
fun TunnelHomeTab(
    state: DashboardUiState,
    updaterState: UpdaterUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onUpdaterIntent: (UpdaterUiIntent) -> Unit,
    onPairingIntent: (PairingUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    val clipboard = LocalClipboardManager.current
    val connected = state.connectionState as? TunnelConnectionState.Connected
    val isPaired = state.pairing.pairingState is AdbPairingState.PairedAndConnected
    val isTunnelOnline = state.connectionState is TunnelConnectionState.Connected

    var forceShowPairing by rememberSaveable { mutableStateOf(false) }
    var dismissPairingGuide by rememberSaveable { mutableStateOf(false) }

    val shouldShowPairing = (!isPaired && !isTunnelOnline && !state.isUsbConnected && !dismissPairingGuide) || forceShowPairing

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

    val (badgeLabel, subtitle, orbTint, orbContainer) = when (val conn = state.connectionState) {
        TunnelConnectionState.Disconnected -> StatusVisual(
            badge = "CHƯA KẾT NỐI",
            subtitle = "Sẵn sàng mở đường hầm bảo mật về Mac mini",
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer
        )
        is TunnelConnectionState.Connecting -> StatusVisual(
            badge = "ĐANG KẾT NỐI...",
            subtitle = "Đang thiết lập kênh TLS về Mac mini",
            tint = ext.statusWarning,
            container = ext.statusWarningContainer
        )
        is TunnelConnectionState.Connected -> StatusVisual(
            badge = "ĐANG TRỰC TUYẾN",
            subtitle = conn.mappedServerPort?.let { "127.0.0.1:$it" } ?: "Đã kết nối thông suốt",
            tint = ext.statusConnected,
            container = ext.statusConnectedContainer
        )
        is TunnelConnectionState.Reconnecting -> StatusVisual(
            badge = "ĐANG NỐI LẠI (${conn.attempt})",
            subtitle = "Thử lại sau ${conn.nextRetryDelayMs / 1000}s",
            tint = ext.statusWarning,
            container = ext.statusWarningContainer
        )
        is TunnelConnectionState.Error -> StatusVisual(
            badge = "GIÁN ĐOẠN KẾT NỐI",
            subtitle = conn.message,
            tint = MaterialTheme.colorScheme.error,
            container = MaterialTheme.colorScheme.errorContainer
        )
    }

    val animatedContainer by animateColorAsState(targetValue = orbContainer, label = "orbContainer")
    val animatedTint by animateColorAsState(targetValue = orbTint, label = "orbTint")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CompactUpdateAlertBanner(state = updaterState, onIntent = onUpdaterIntent)

        if (shouldShowPairing) {
            // Bước 1: Kích hoạt ghép đôi (Hiển thị phẳng trực tiếp, không chia tab)
            PairingSection(
                state = state.pairing,
                onIntent = onPairingIntent,
                onSwitchToConnect = {
                    dismissPairingGuide = true
                    forceShowPairing = false
                }
            )
        } else {
            // Bước 2: Bảng điều khiển kết nối 1-chạm (Single-Layer Switchboard Card)
            TunnelSwitchboardCard(
                state = state,
                connected = connected,
                animatedContainer = animatedContainer,
                animatedTint = animatedTint,
                badgeLabel = badgeLabel,
                subtitle = subtitle,
                nowEpochMs = nowEpochMs,
                onIntent = onIntent,
                onCopyMappedAddr = { clipboard.setText(AnnotatedString(it)) }
            )

            // Thẻ đường truyền kích hoạt phẳng đơn lớp
            ActiveConnectionModeCard(
                wifiIp = state.localWifiIp,
                adbPort = state.localAdbPortInput.ifBlank { "5555" },
                isUsbConnected = state.isUsbConnected,
                isAtHomeWifi = state.isAtHomeWifi,
                homeSsid = state.trustedHomeSsidInput.ifBlank { state.currentWifiSsid },
                onCopyCommand = { cmd -> clipboard.setText(AnnotatedString(cmd)) }
            )

            // Nút phụ tái ghép đôi
            TextButton(
                onClick = {
                    forceShowPairing = true
                    dismissPairingGuide = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cần ghép đôi lại ADB? Chạm vào đây",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private data class StatusVisual(
    val badge: String,
    val subtitle: String,
    val tint: androidx.compose.ui.graphics.Color,
    val container: androidx.compose.ui.graphics.Color
)
