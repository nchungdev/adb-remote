package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import app.duinch.adbagent.core.designsystem.AdbExtendedColors
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.parser.RemoteQrPayloadParser
import app.duinch.remotetunnel.ui.ConnectionTelemetryBar
import app.duinch.remotetunnel.ui.DualChannelConnectContent
import app.duinch.remotetunnel.ui.RemoteConnectContent
import app.duinch.remotetunnel.ui.RemoteConnectionState
import app.duinch.remotetunnel.ui.RemoteTelemetryData
import app.duinch.remotetunnel.ui.RemoteTransportChannel
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
    val isTunnelOnline = state.connectionState is TunnelConnectionState.Connected

    val activeConnectedEpoch = when {
        connected != null -> connected.connectedAtEpochMs
        state.isUsbConnected -> state.usbConnectedAtEpochMs
        else -> 0L
    }

    val nowEpochMs by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = activeConnectedEpoch
    ) {
        if (activeConnectedEpoch > 0L) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val visual = resolveStatusVisual(state = state, ext = ext)
    val animatedContainer by animateColorAsState(targetValue = visual.container, label = "orbContainer")
    val animatedTint by animateColorAsState(targetValue = visual.tint, label = "orbTint")

    val telemetry = remember(state.connectionState, state.serverUrlInput, state.remoteRoomCode, state.isUsbConnected, nowEpochMs) {
        val isTunnelActive = state.connectionState is TunnelConnectionState.Connecting ||
            state.connectionState is TunnelConnectionState.Reconnecting ||
            state.connectionState is TunnelConnectionState.Connected

        val connState = when {
            state.connectionState is TunnelConnectionState.Connecting ||
                state.connectionState is TunnelConnectionState.Reconnecting -> RemoteConnectionState.Connecting
            state.connectionState is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
            state.isUsbConnected -> RemoteConnectionState.Connected
            else -> RemoteConnectionState.Disconnected
        }
        val channel = when {
            isTunnelActive && state.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            isTunnelActive && state.remoteRoomCode.isNotBlank() -> RemoteTransportChannel.WebRtcDirect
            isTunnelActive && (state.selectedMode == AdbConnectionMode.LAN || state.isAtHomeWifi) -> RemoteTransportChannel.LanWifi
            state.isUsbConnected -> RemoteTransportChannel.UsbCable
            state.selectedMode == AdbConnectionMode.LAN || state.isAtHomeWifi -> RemoteTransportChannel.LanWifi
            state.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            state.remoteRoomCode.isNotBlank() -> RemoteTransportChannel.WebRtcDirect
            else -> RemoteTransportChannel.None
        }
        val ping = when {
            state.isUsbConnected -> (1L..3L).random() // USB direct low latency ping
            else -> null
        }
        val uptimeSec = if (activeConnectedEpoch > 0L && nowEpochMs > activeConnectedEpoch) {
            (nowEpochMs - activeConnectedEpoch) / 1000
        } else {
            null
        }
        RemoteTelemetryData(
            state = connState,
            channel = channel,
            host = if (state.isUsbConnected) "" else state.serverUrlInput,
            roomCode = state.remoteRoomCode,
            pingMs = ping,
            uptimeSec = uptimeSec ?: 0L
        )
    }

    val isWifiConnected = remember(state.activeNetwork, state.localWifiIp) {
        state.activeNetwork == NetworkType.WIFI || state.localWifiIp.isNotBlank()
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isTunnelOnline) {
            // Active session switchboard (State 3: Đã kết nối)
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
        } else {
            // Tier 1: Trạng thái ADB Thiết bị gộp Telemetry (USB / Không dây / Chưa bật)
            AdbDeviceStatusCard(
                isUsbConnected = state.isUsbConnected,
                pairingState = state.pairing.pairingState,
                telemetry = telemetry,
                onOpenPairing = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
            )

            // Tier 2: Kết nối Máy Chủ (Cùng Wi-Fi LAN / Khác mạng Từ Xa)
            DualChannelConnectContent(
                initialLanAddress = state.serverUrlInput,
                initialRemoteTarget = state.remoteRoomCode,
                telemetry = telemetry,
                isUsbConnected = state.isUsbConnected,
                isWifiConnected = isWifiConnected,
                serverRemoteUrl = state.serverQuickTunnelUrl,
                isServerRemoteActive = state.isServerRemoteActive,
                recentTunnels = remember(state.serverQuickTunnelUrl, state.serverUrlInput, state.recentTunnelUrls) {
                    (state.recentTunnelUrls + listOf(state.serverQuickTunnelUrl, state.serverUrlInput))
                        .filter { it.isNotBlank() && (it.contains("trycloudflare.com") || it.startsWith("http")) }
                        .distinct()
                        .take(3)
                },
                onConnect = { lan, remote ->
                    val isUrl = remote.startsWith("http://") || remote.startsWith("https://") || remote.contains("trycloudflare.com")
                    val targetServer = when {
                        lan.isNotBlank() -> lan
                        isUrl -> remote
                        state.serverQuickTunnelUrl.isNotBlank() -> state.serverQuickTunnelUrl
                        else -> state.serverUrlInput
                    }
                    val targetRoom = when {
                        !isUrl && remote.isNotBlank() -> remote
                        state.remoteRoomCode.isNotBlank() -> state.remoteRoomCode
                        else -> "default"
                    }
                    onIntent(DashboardUiIntent.ApplyQrRemoteConfig(roomCode = targetRoom, serverUrl = targetServer))
                },
                onDisconnect = { onIntent(DashboardUiIntent.ToggleTunnel) },
                onScanQr = { onIntent(DashboardUiIntent.ScanQrCode) }
            )
        }
    }
}
